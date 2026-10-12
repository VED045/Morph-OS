package ai.morphos.launcher.data

import android.content.*
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import ai.morphos.launcher.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton class PhoneInference @Inject constructor(@ApplicationContext private val context: Context) {
    private val lock = Mutex()
    private val folder get() = File(context.filesDir, "on-device")
    private val model get() = File(folder, "model.litertlm")
    private val metadata get() = context.getSharedPreferences("local_model", Context.MODE_PRIVATE)
    fun visionEnabled() = metadata.getBoolean("vision", false)
    fun visionEnabled(value: Boolean) { metadata.edit().putBoolean("vision", value).putBoolean("verified", false).apply() }
    fun installed() = model.isFile && model.length() >= 1048576
    fun name() = metadata.getString("name", "Local model").orEmpty()
    fun status() = if (installed()) "${name()} · ${model.length() / 1048576} MB · ${if (metadata.getBoolean("verified", false)) "runtime checked" else "not yet checked"}" else "No on-device model installed"
    suspend fun importModel(uri: Uri) = lock.withLock { withContext(Dispatchers.IO) {
        var display = ""
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) display = it.getString(0).orEmpty() }
        require(display.endsWith(".litertlm", true)) { "Choose a .litertlm model file" }
        folder.mkdirs()
        val pending = File(folder, "import.tmp")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                pending.outputStream().use { output ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) { ensureActive(); val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 8589934592L && folder.usableSpace > 134217728L) { "Model exceeds size or available storage" }; output.write(buffer, 0, n) }
                    require(total >= 1048576) { "Model file is too small" }; output.flush()
                }
            }
            check(pending.renameTo(model)) { "Could not install model" }
            metadata.edit().putString("name", display.take(100)).putBoolean("verified", false).apply()
        } finally { pending.delete() }
    } }
    suspend fun delete() = lock.withLock { withContext(Dispatchers.IO) { model.delete(); metadata.edit().clear().apply() } }
    private suspend fun request(prompt: String, image: String? = null): String = lock.withLock {
        val picture = image?.let { File(folder, "reference.jpg").also { file -> val bytes = android.util.Base64.decode(it, android.util.Base64.DEFAULT); require(bytes.size <= 1000000); file.writeBytes(bytes) } }
        try {
        require(installed()) { "Import an on-device model first" }
        withTimeout(120000) { suspendCancellableCoroutine { continuation ->
            var bound = false
            lateinit var connection: ServiceConnection
            fun cleanup() { if (bound) { bound = false; runCatching { context.unbindService(connection) } } }
            val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
                val error = message.data.getString("error")
                cleanup()
                if (continuation.isActive) { if (error != null) continuation.resumeWithException(IllegalStateException(error)) else continuation.resume(message.data.getString("result").orEmpty()) }
                true
            })
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    runCatching { Messenger(binder).send(Message.obtain(null, 1).apply { data = Bundle().apply { putString("prompt", prompt); putBoolean("vision", visionEnabled()); putString("image", picture?.absolutePath) }; replyTo = reply }) }.onFailure { cleanup(); if (continuation.isActive) continuation.resumeWithException(it) }
                }
                override fun onServiceDisconnected(name: ComponentName) { cleanup(); if (continuation.isActive) continuation.resumeWithException(IllegalStateException("On-device inference stopped")) }
                override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
                override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
            }
            bound = context.bindService(Intent(context, PhoneInferenceService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound && continuation.isActive) continuation.resumeWithException(IllegalStateException("On-device runtime unavailable"))
            continuation.invokeOnCancellation { cleanup() }
        } }
        } finally { picture?.delete() }
    }
    suspend fun reference(request: ImageDesignRequest): DesignProposal {
        require(visionEnabled())
        val prompt = "Design a launcher inspired by this image and the user request. Return only JSON with explanation and patch. patch may contain columns (3..6), cornerRadius (0..40), iconRadius (0..28), monochromeIcons boolean, typography (sans/mono/serif), wallpaperStyle (SOLID/AURORA/ORBS/GRAIN), clockStyle (DIGITAL/DOT/STACKED), homeStyle (CALM/GEOMETRIC/SHELF/ORCHARD/ONE), and light/dark palettes with background,surface,foreground,muted,accent #RRGGBB. No code or extra keys. Current: ${Json.encodeToString(request.current)}. Request: ${request.prompt}. /no_think"
        return LocalProposal.design(this.request(prompt, request.imageBase64), request.current, request.revision, name())
    }
    suspend fun verify() { request("Return exactly {\"ready\":true}"); metadata.edit().putBoolean("verified", true).apply() }
    suspend fun design(request: DesignRequest): DesignProposal {
        val prompt = "Return only a JSON object with explanation (under 100 words) and patch. Patch changes only requested theme fields: columns 3..6, drawer GRID/LIST/CATEGORIES/ALPHABETICAL/RADIAL, cornerRadius 0..40, iconRadius 0..28, monochromeIcons boolean, typography sans/mono/serif, spacing 8..24, dockOpacity 0.2..1, clockStyle DIGITAL/DOT/STACKED, homeStyle CALM/GEOMETRIC/SHELF/ORCHARD/ONE, wallpaperStyle SOLID/AURORA/ORBS/GRAIN, gradient/reducedMotion boolean, or light/dark palettes with background,surface,foreground,muted,accent #RRGGBB colors. No code, actions, identifiers or extra keys. Example: {\"explanation\":\"Softer corners.\",\"patch\":{\"cornerRadius\":24}}. Minimal patch. Current theme: ${Json.encodeToString(request.current)}. User request: ${request.prompt}. /no_think"
        return LocalProposal.design(this.request(prompt), request.current, request.revision, name())
    }
    suspend fun widget(request: WidgetRequest): WidgetProposal {
        val prompt = "Return only JSON with explanation and widget. widget permits title (1..40 chars), kind CLOCK/BATTERY/TIMER/COUNTER/BIRTHDAYS/CALENDAR, shape ROUNDED/CIRCLE/HEXAGON/STAR/RECTANGLE, durationSeconds (1..86400). No other keys or code. Example: {\"explanation\":\"Three-minute timer.\",\"widget\":{\"title\":\"Focus\",\"kind\":\"TIMER\",\"shape\":\"ROUNDED\",\"durationSeconds\":180}}. User request: ${request.prompt}. /no_think"
        return LocalProposal.widget(this.request(prompt), request.revision, name())
    }
}
