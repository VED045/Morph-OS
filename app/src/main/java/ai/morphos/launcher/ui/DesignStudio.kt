package ai.morphos.launcher.ui

import ai.morphos.launcher.domain.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import ai.morphos.launcher.data.ReferenceImage
import kotlinx.coroutines.launch

@Composable
internal fun DesignStudio(state: LauncherState, model: LauncherViewModel, style: Style, dark: Boolean, back: () -> Unit) {
    var prompt by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable { mutableStateOf("") }
    // A client credential lives in memory only. Provider secrets stay on the backend.
    var token by remember { mutableStateOf("") }
    var complex by rememberSaveable { mutableStateOf(false) }
    var connection by rememberSaveable { mutableStateOf(false) }
    var image by remember { mutableStateOf<String?>(null) }
    var imageError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        image = null
        if (uri != null) scope.launch {
            try { image = ReferenceImage.read(context, uri); imageError = null }
            catch (_: Exception) { imageError = "Choose a PNG or JPEG screenshot. Images are resized before upload." }
        }
    }
    val proposal by model.proposal.collectAsStateWithLifecycle()
    val busy by model.designing.collectAsStateWithLifecycle()
    val error by model.designError.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Header("Design your space", "Preview → Apply → Undo", style, back)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("A home that feels like you.", color = style.text, fontSize = 32.sp, fontWeight = FontWeight.Light)
            Text("Describe a change. Your selected AI proposes a design; you decide what lands on your home.", color = style.muted)
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (image == null) "Add screenshot reference" else "Change screenshot") }
            imageError?.let { Text(it, color = style.muted) }
            if (image != null) {
                Text("Reference ready. Extract colors stays on this phone. Analyze uses your selected AI route: on-phone vision, on-phone palette plus text model, or the configured Nebius vision backend.", color = style.muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.proposeReference(prompt, image!!, endpoint, token) }, enabled = !busy) { Text("Analyze & preview") }
                    TextButton(onClick = { model.paletteReference(image!!) }, enabled = !busy) { Text("Extract colors") }
                }
                Row {
                    TextButton(onClick = { image = null }) { Text("Remove image") }
                }
            }
            OutlinedTextField(prompt, { prompt = it.take(2000) }, label = { Text("What would you change?") }, placeholder = { Text("Nothing icons, a soft Oxygen drawer…") }, modifier = Modifier.fillMaxWidth().testTag("design_prompt"), minLines = 3, shape = RoundedCornerShape(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!complex, { complex = false }, label = { Text("Fast route") })
                FilterChip(complex, { complex = true }, label = { Text("Reasoning route") })
            }
            Button(onClick = { model.propose(prompt, endpoint, token, complex) }, enabled = prompt.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("generate_design")) { Text(if (busy) "Designing…" else "Preview design") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            Text("Try offline", color = style.muted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Use monochrome icons", "Use a list drawer", "Use a dot clock", "Use a minimal black theme").forEach { command -> AssistChip(onClick = { prompt = command }, label = { Text(command) }) }
            }
            proposal?.let { result ->
                val previewSpec = runCatching { val current = model.currentTheme(); DesignEngine.apply(current.copy(columns = state.preferences.columnsOverride ?: current.columns, drawer = state.preferences.drawerOverride ?: current.drawer), result.patch) }.getOrNull()
                if (previewSpec != null) {
                    val preview = Style(previewSpec, if (dark) previewSpec.dark else previewSpec.light)
                    Surface(color = preview.bg, shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, preview.muted.copy(alpha = .25f))) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("PREVIEW", color = preview.accent, fontSize = 11.sp, letterSpacing = 3.sp)
                            if (previewSpec.clockStyle == "DOT") DotTime("09:41", preview.text)
                            else Text("09:41", color = preview.text, fontFamily = preview.font, fontSize = 54.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(previewSpec.columns) { Box(Modifier.size(36.dp).background(if (previewSpec.monochromeIcons) preview.text else preview.accent, RoundedCornerShape(previewSpec.iconRadius.dp))) } }
                            Text("${previewSpec.drawer.name.lowercase()} drawer · ${previewSpec.clockStyle.lowercase()} clock", color = preview.muted)
                        }
                    }
                    Text(result.explanation, color = style.text)
                    Text(when (result.source) { "image" -> "Local image colors · No model call"; "local" -> "Offline command · No model call"; "device" -> "On-device model · ${result.model}"; else -> "Nebius Token Factory · ${result.model}" }, color = style.muted, fontSize = 12.sp)
                    Button(onClick = model::applyProposal, enabled = result.revision == state.preferences.revision, modifier = Modifier.fillMaxWidth().testTag("apply_design")) { Text("Apply to my launcher") }
                    if (result.revision != state.preferences.revision) Text("Your layout changed. Generate a fresh preview.", color = style.muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = model::undo, enabled = state.preferences.canUndo, modifier = Modifier.testTag("undo_design")) { Text("Undo") }
                OutlinedButton(onClick = model::redo, enabled = state.preferences.canRedo, modifier = Modifier.testTag("redo_design")) { Text("Redo") }
            }
            LocalAiSetup(model, style, state.preferences.localAiMode)
            TextButton(onClick = { connection = !connection }) { Text("Nebius connection") }
            if (connection) {
                Text("Model IDs and the Nebius key are configured on the backend. Enter its HTTPS URL and a separate MorphOS client token here. The token is cleared when this screen is destroyed.", color = style.muted, fontSize = 12.sp)
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS backend URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it }, label = { Text("MorphOS client token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
