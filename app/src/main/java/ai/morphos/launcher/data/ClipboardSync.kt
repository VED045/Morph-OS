package ai.morphos.launcher.data

import android.content.*
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.*
import java.net.URLEncoder
import java.security.*
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject

fun InputStream.boundedBytes(maximum: Int): ByteArray {
    val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if (count < 0) break; require(out.size() + count <= maximum) { "Payload exceeds limit" }; out.write(buffer, 0, count) }
    return out.toByteArray()
}
data class SharedClip(val id: String, val mime: String, val name: String, val text: String?, val file: File?)

@Singleton class ClipboardSync @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("paired_clipboard", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val folder get() = File(context.cacheDir, "shared-clips").apply { mkdirs() }
    val status = MutableStateFlow("Pair a Windows companion to start")
    val clips = MutableStateFlow<List<SharedClip>>(emptyList())
    val incoming = MutableStateFlow<ClipData?>(null)
    val enabled = MutableStateFlow(false)
    private var lastText = ""
    @Volatile private var epoch = 0
    init { folder.listFiles()?.forEach { it.delete() } }
    fun address() = prefs.getString("address", "").orEmpty()
    fun paired() = address().isNotBlank()
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey("morphos_clipboard", null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("morphos_clipboard", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    suspend fun pair(address: String, fingerprint: String, key: String) = withContext(Dispatchers.IO) {
        val url = address.trim().trimEnd('/').toHttpUrl()
        require(url.isHttps && url.encodedPath == "/" && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
        val pin = fingerprint.replace(":", "").trim().lowercase()
        require(pin.matches(Regex("[0-9a-f]{64}")) && key.length in 32..100)
        // Verify this exact certificate and credential before saving the pairing.
        val client = client(pin)
        client.newCall(Request.Builder().url(url.newBuilder().addPathSegment("state").build()).header("Authorization", "Bearer $key").build()).execute().use { require(it.isSuccessful) { "Pairing was rejected" } }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.doFinal(key.toByteArray())
        prefs.edit().putString("address", url.toString().trimEnd('/')).putString("pin", pin).putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).putString("secret", Base64.encodeToString(encrypted, Base64.NO_WRAP)).putLong("cursor", 0).apply()
        enabled.value = false; status.value = "Paired. Turn on sync while MorphOS is open, or send manually."
    }
    fun disconnect() { epoch++; enabled.value = false; prefs.edit().clear().apply(); status.value = "Disconnected"; clear() }
    fun clear() { clips.value = emptyList(); folder.listFiles()?.forEach { it.delete() } }
    private fun token(): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.DEFAULT))) }
        return cipher.doFinal(Base64.decode(prefs.getString("secret", ""), Base64.DEFAULT)).toString(Charsets.UTF_8)
    }
    private fun client(pin: String): OkHttpClient {
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) { throw java.security.cert.CertificateException("Client certificates not supported") }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val cert = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("No certificate")
                cert.checkValidity()
                val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
                if (!MessageDigest.isEqual(actual.toByteArray(), pin.toByteArray())) throw java.security.cert.CertificateException("Companion fingerprint mismatch")
            }
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, trust).hostnameVerifier { _, session ->
            runCatching { trust.checkServerTrusted(arrayOf(session.peerCertificates.first() as X509Certificate), "RSA"); true }.getOrDefault(false)
        }.followRedirects(false).followSslRedirects(false).connectTimeout(10, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
    }
    private fun call(path: String, body: RequestBody? = null, name: String = ""): Response {
        require(paired()) { "Pair your laptop first" }
        val request = Request.Builder().url(address() + path).header("Authorization", "Bearer " + token())
        if (body != null) request.post(body)
        if (name.isNotBlank()) request.header("X-File-Name", URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
        val response = client(prefs.getString("pin", "").orEmpty()).newCall(request.build()).execute()
        if (!response.isSuccessful) { val code = response.code; response.close(); error("Companion returned $code") }
        return response
    }
    suspend fun sendText(text: String) = withContext(Dispatchers.IO) {
        require(text.isNotBlank() && text.length <= 32768)
        call("/text", JSONObject().put("text", text).toString().toRequestBody("application/json".toMediaType())).use { }
        lastText = text; status.value = "Text sent to Windows"
    }
    suspend fun sendUri(uri: Uri) = lock.withLock { withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        require(mime.startsWith("image/") || mime.startsWith("video/")) { "Choose an image or video" }
        var name = "Shared media"
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0).take(120) }
        val pending = File(folder, "upload.tmp")
        try {
            context.contentResolver.openInputStream(uri).use { input -> requireNotNull(input); pending.outputStream().use { out -> copyBounded(input, out, 104857600L) } }
            require(pending.length() > 0)
            val body = object : RequestBody() {
                override fun contentType() = mime.toMediaType()
                override fun contentLength() = pending.length()
                override fun writeTo(sink: BufferedSink) { pending.inputStream().use { it.copyTo(sink.outputStream()) } }
            }
            call("/blob", body, name).use { }
            status.value = "$name sent to Windows"
        } finally { pending.delete() }
    } }
    suspend fun sendClip(clip: ClipData) {
        require(clip.itemCount <= 10)
        for (i in 0 until clip.itemCount) { val item = clip.getItemAt(i); if (item.uri != null) sendUri(item.uri) else item.text?.toString()?.let { sendText(it) } }
    }
    suspend fun clipboardChanged() {
        if (!enabled.value || !paired()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip ?: return
        if (clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true || clip.description.label == "MorphOS synced") return
        if (clip.itemCount == 1 && clip.getItemAt(0).text?.toString() == lastText) return
        sendClip(clip)
    }
    suspend fun poll(copyToClipboard: Boolean = enabled.value) = lock.withLock { withContext(Dispatchers.IO) {
        val session = epoch
        val response = call("/state?after=" + prefs.getLong("cursor", 0)).use { JSONObject(requireNotNull(it.body).byteStream().boundedBytes(900000).toString(Charsets.UTF_8)) }
        val items = response.getJSONArray("items"); require(items.length() <= 20)
        for (i in 0 until items.length()) {
            check(session == epoch && paired()) { "Pairing changed" }
            val row = items.getJSONObject(i); val id = row.getString("id"); require(id.matches(Regex("[0-9]{1,20}")))
            if (row.getString("origin") == "windows") {
                val mime = row.getString("mime"); val name = row.getString("name").take(120)
                val text = if (mime == "text/plain") row.getString("text").also { require(it.length <= 32768) } else null
                val file = if (text == null) {
                    val extension = mapOf("image/png" to "png", "image/jpeg" to "jpg", "image/webp" to "webp", "image/gif" to "gif", "image/bmp" to "bmp", "video/mp4" to "mp4", "video/quicktime" to "mov", "video/webm" to "webm", "video/x-matroska" to "mkv")[mime] ?: error("Unsupported file type")
                    require(row.getLong("size") in 1..104857600L)
                    val target = File(folder, "$id.$extension")
                    try {
                        call("/blob/$id").use { download -> target.outputStream().use { copyBounded(requireNotNull(download.body).byteStream(), it, 104857600L) } }
                        val hash = MessageDigest.getInstance("SHA-256")
                        target.inputStream().use { input -> val bytes = ByteArray(65536); while (true) { val count = input.read(bytes); if (count < 0) break; hash.update(bytes, 0, count) } }
                        require(target.length() == row.getLong("size") && hash.digest().joinToString("") { "%02x".format(it) } == row.getString("sha256")) { "Transfer checksum mismatch" }
                        target
                    } catch (e: Exception) { target.delete(); throw e }
                } else null
                val item = SharedClip(id, mime, name, text, file)
                check(session == epoch && paired()) { "Pairing changed" }
                var retained = (clips.value + item).takeLast(20)
                while (retained.sumOf { it.file?.length() ?: 0L } > 262144000L) retained = retained.drop(1)
                clips.value = retained
                val keep = clips.value.mapNotNull { it.file?.name }.toSet()
                folder.listFiles()?.filter { it.name != "upload.tmp" && it.name !in keep }?.forEach { it.delete() }
                if (copyToClipboard) withContext(Dispatchers.Main) { copy(item) }
                status.value = "Received from Windows"
            }
            prefs.edit().putLong("cursor", id.toLong()).apply()
        }
    } }
    fun copy(item: SharedClip) {
        val clip = if (item.text != null) ClipData.newPlainText("MorphOS synced", item.text) else ClipData.newUri(context.contentResolver, "MorphOS synced", uri(item))
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }
    fun uri(item: SharedClip): Uri = FileProvider.getUriForFile(context, context.packageName + ".shared", requireNotNull(item.file))
    private fun copyBounded(input: InputStream, output: OutputStream, maximum: Long) {
        var total = 0L; val buffer = ByteArray(65536)
        while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= maximum && folder.usableSpace > 33554432); output.write(buffer, 0, count) }
    }
}
