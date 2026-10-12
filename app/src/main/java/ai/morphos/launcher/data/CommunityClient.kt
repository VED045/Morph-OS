package ai.morphos.launcher.data

import ai.morphos.launcher.domain.ThemeSpec
import ai.morphos.launcher.domain.ThemeValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CommunityTheme(val id: String, val author: String, val theme: ThemeSpec)
object CommunityClient {
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).followRedirects(false).build()
    suspend fun request(host: String, path: String, key: String = "", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val url = host.trim().trimEnd('/').toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "Use an HTTPS community URL" }
        val request = Request.Builder().url(url.toString().trimEnd('/') + "/community/" + path)
        if (key.isNotBlank()) request.header("Authorization", "Bearer $key")
        if (body != null) request.post(body.toString().toRequestBody("application/json".toMediaType()))
        client.newCall(request.build()).execute().use { response ->
            check(response.isSuccessful) { "Community returned ${response.code}. Check your URL, invite or creator key." }
            val stream = requireNotNull(response.body).byteStream()
            val data = stream.boundedBytes(400000)
            require(data.size <= 400000)
            JSONObject(data.toString(Charsets.UTF_8))
        }
    }
    suspend fun browse(host: String, before: Long? = null): Pair<List<CommunityTheme>, Long?> {
        val result = request(host, "themes" + (before?.let { "?before=$it" } ?: ""))
        val items = result.getJSONArray("items")
        require(items.length() <= 40)
        return (0 until items.length()).map { i -> val row = items.getJSONObject(i); CommunityTheme(row.getString("id"), row.getString("author"), ThemeValidator.decode(row.getJSONObject("theme").toString())) } to if (result.isNull("next")) null else result.getLong("next")
    }
    suspend fun publish(host: String, key: String, theme: ThemeSpec) = request(host, "themes", key, JSONObject().put("theme", JSONObject(Json.encodeToString(ThemeValidator.validate(theme)))))
}
