package ai.morphos.launcher.data

import android.app.Service
import android.content.Intent
import android.os.*
import android.os.Message
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import java.io.File

// Native inference lives in a separate private process; launcher rendering keeps its own heap.
class PhoneInferenceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        val reply = message.replyTo
        val prompt = message.data.getString("prompt").orEmpty()
        val vision = message.data.getBoolean("vision")
        val imagePath = message.data.getString("image")
        if (job?.isActive == true || prompt.length !in 1..12000) {
            runCatching { reply.send(Message.obtain(null, 2).apply { data = Bundle().apply { putString("error", "On-device inference is busy or request is invalid") } }) }
        } else job = scope.launch {
            val data = Bundle()
            try {
                Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
                val model = File(filesDir, "on-device/model.litertlm")
                require(model.isFile)
                val runtime = Engine(EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU(), visionBackend = if (vision) Backend.GPU() else null, maxNumTokens = if (vision) 2048 else 1024, cacheDir = File(cacheDir, "inference").apply { mkdirs() }.absolutePath))
                try {
                    runtime.initialize()
                    runtime.createConversation(ConversationConfig(automaticToolCalling = false, samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0), extraContext = mapOf("enable_thinking" to false))).use { conversation ->
                        val result = StringBuilder()
                        val input = if (imagePath == null) Contents.of(prompt) else { require(vision && imagePath == File(filesDir, "on-device/reference.jpg").absolutePath); Contents.of(Content.ImageFile(imagePath), Content.Text(prompt)) }
                        withTimeout(90000) { conversation.sendMessageAsync(input).collect { token ->
                            result.append(token.toString()); require(result.length <= 8192) { "Model response exceeds limit" }
                        } }
                        data.putString("result", result.toString())
                    }
                } finally { if (runtime.isInitialized()) runtime.close() }
            } catch (_: TimeoutCancellationException) { data.putString("error", "On-device generation timed out. Try a smaller model or simpler request.") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { data.putString("error", "Model could not run or return a supported proposal. Check model compatibility and available memory.") }
            catch (_: LinkageError) { data.putString("error", "Native runtime does not support this device") }
            runCatching { reply.send(Message.obtain(null, 2).apply { this.data = data }) }
        }
        true
    })
    override fun onBind(intent: Intent) = messenger.binder
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
