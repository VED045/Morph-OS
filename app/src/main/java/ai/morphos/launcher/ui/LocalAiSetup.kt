package ai.morphos.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

@Composable internal fun LocalAiSetup(model: LauncherViewModel, style: Style, mode: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var status by remember { mutableStateOf(model.phoneInference.status()) }
    var vision by remember { mutableStateOf(model.phoneInference.visionEnabled()) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch {
        busy = true; result = null
        try { model.phoneInference.importModel(uri); status = model.phoneInference.status(); result = "Model imported. Run the check before using it." }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { result = "Import failed. Choose a compatible .litertlm file under 8 GB and check free storage." }
        finally { busy = false }
    } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("On-device AI", color = style.text, fontSize = 18.sp)
        Text(status, color = style.muted, fontSize = 12.sp, modifier = Modifier.testTag("local_model_status"))
        Text("Runs on your Android phone using LiteRT-LM. Import a CPU .litertlm model compatible with LiteRT-LM 0.13.1. The pinned Qwen3 0.6B INT4 in the model guide was tested here. Import its file below; no model is bundled. Smaller models handle simpler edits and are not equivalent to Nemotron Ultra.", color = style.muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("off" to "Nebius", "fallback" to "Backup", "local" to "On phone").forEach { (value, title) -> FilterChip(mode == value, { model.localAiMode(value) }, label = { Text(title) }) } }
        Text(when(mode) { "local" -> "AI requests stay on this phone. Images use local palette extraction plus your text model, or the vision runtime when enabled below."; "fallback" -> "If Nebius fails, an installed model can propose a validated local edit."; else -> "Connected generation uses your Nebius backend." }, color = style.muted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.testTag("import_local_model")) { Text("Import model") }
            OutlinedButton(onClick = { scope.launch {
                busy = true; result = "Checking runtime…"
                try { model.phoneInference.verify(); status = model.phoneInference.status(); result = "The runtime loaded and generated a response. Proposal accuracy still depends on the model." }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { result = "Runtime check failed. Check model format, device support and free memory." }
                finally { busy = false }
            } }, enabled = !busy && model.phoneInference.installed(), modifier = Modifier.testTag("verify_local_model")) { Text("Check model") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        result?.let { Text(it, color = style.muted, fontSize = 12.sp) }
        Row {
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/VED045/Morph-OS/blob/main/docs/ON_DEVICE_AI.md"))) } }) { Text("Model guide") }
            TextButton(onClick = { scope.launch { model.phoneInference.delete(); status = model.phoneInference.status(); result = null } }, enabled = !busy && model.phoneInference.installed()) { Text("Delete model") }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("Image-capable model (GPU vision)", modifier = Modifier.weight(1f), color = style.text); Switch(vision, { vision = it; model.phoneInference.visionEnabled(it) }, enabled = !busy) }
        Text("Enable only for a compatible multimodal model such as Gemma 3n. It requires substantially more RAM and a supported GPU; this route still needs physical-phone validation. Text-only Qwen uses local image colors instead.", color = style.muted, fontSize = 11.sp)
        Text("Models stay in private app storage. Gemma downloads require accepting its license at the source. Inference uses a separate process and releases its engine after each request.", color = style.muted, fontSize = 11.sp)
    }
}
