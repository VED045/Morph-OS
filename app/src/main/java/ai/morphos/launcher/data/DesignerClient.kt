package ai.morphos.launcher.data

import ai.morphos.launcher.domain.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import java.net.URI
import javax.inject.Inject

interface DesignerApi {
    @POST("reference") suspend fun reference(@Header("Authorization") authorization: String, @Body request: ImageDesignRequest): DesignProposal
    @POST("design") suspend fun design(@Header("Authorization") authorization: String, @Body request: DesignRequest): DesignProposal
    @POST("widget") suspend fun widget(@Header("Authorization") authorization: String, @Body request: WidgetRequest): WidgetProposal
}
class DesignerClient @Inject constructor() {
    private fun api(endpoint: String, token: String): DesignerApi {
        val url = URI(endpoint.trim())
        require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null && url.query == null && url.fragment == null) { "Enter an HTTPS backend URL" }
        require(token.length in 16..512) { "Enter your MorphOS client token, not the Nebius key" }
        val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS)
            .callTimeout(80, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val body = response.body ?: return@addInterceptor response
                val output = java.io.ByteArrayOutputStream()
                body.byteStream().use { stream ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = stream.read(buffer); if (count < 0) break
                        if (output.size() + count > 16384) { response.close(); throw java.io.IOException("Response exceeds limit") }
                        output.write(buffer, 0, count)
                    }
                }
                response.newBuilder().body(output.toByteArray().toResponseBody(body.contentType())).build()
            }.build()
        return Retrofit.Builder().baseUrl(endpoint.trim().trimEnd('/') + "/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build()
            .create(DesignerApi::class.java)
    }
    suspend fun design(endpoint: String, token: String, request: DesignRequest): DesignProposal {
        return api(endpoint, token).design("Bearer $token", request).also {
                require(it.revision == request.revision && it.source == "nebius" && it.explanation.length <= 500)
                DesignEngine.apply(request.current, it.patch)
            }
    }
    suspend fun widget(endpoint: String, token: String, request: WidgetRequest): WidgetProposal = api(endpoint, token).widget("Bearer $token", request).also {
        require(it.source == "nebius" && it.revision == request.revision && it.explanation.length <= 500)
        WidgetRegistry.validate(it.widget)
    }
    suspend fun reference(endpoint: String, token: String, request: ImageDesignRequest): DesignProposal = api(endpoint, token).reference("Bearer $token", request).also {
        require(it.source == "nebius" && it.revision == request.revision)
        DesignEngine.apply(request.current, it.patch)
    }
}
