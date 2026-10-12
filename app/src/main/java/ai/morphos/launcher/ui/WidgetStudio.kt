package ai.morphos.launcher.ui

import ai.morphos.launcher.domain.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID

@Composable internal fun WidgetStudio(state: LauncherState, model: LauncherViewModel, style: Style, back: () -> Unit) {
    var prompt by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf<WidgetSpec?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var endpoint by rememberSaveable { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var connection by rememberSaveable { mutableStateOf(false) }
    val ai by model.widgetProposal.collectAsStateWithLifecycle()
    val busy by model.designing.collectAsStateWithLifecycle()
    val inferenceError by model.designError.collectAsStateWithLifecycle()
    LaunchedEffect(ai) { ai?.let { draft = it.widget; revision = it.revision } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Header("Widgets", "Choose a widget or describe your own", style, back)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Widget gallery", color = style.text, fontSize = 20.sp)
            listOf("Clock" to "Clock", "Battery" to "Circular battery", "Timer" to "20 minute timer", "Counter" to "Habit counter", "Birthdays" to "Birthday widget", "Calendar" to "Calendar meetings").chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (title, template) ->
                        val sample = remember(template) { WidgetRegistry.local(template, "preview_" + title.lowercase())!! }
                        Column(Modifier.weight(1f)) {
                            WidgetCard(sample, style, {}, Modifier.fillMaxWidth().height(136.dp), fit = true, compact = true)
                            TextButton(onClick = { model.addWidget(sample.copy(id = "w_" + UUID.randomUUID().toString().take(8)), state.preferences.revision) }, enabled = state.preferences.widgets.size < 8, modifier = Modifier.fillMaxWidth()) { Text("+ $title") }
                        }
                    }
                }
            }
            Text("Create a widget", color = style.text, fontSize = 20.sp)
            OutlinedTextField(prompt, { prompt = it.take(1000) }, label = { Text("Describe a widget") }, placeholder = { Text("Create a hexagonal 20 minute timer") }, modifier = Modifier.fillMaxWidth().testTag("widget_prompt"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Star clock", "Circular battery", "20 minute timer", "Hexagonal birthday widget", "Calendar meetings", "Habit counter").forEach { text -> AssistChip(onClick = { prompt = text }, label = { Text(text) }) }
            }
            Button(onClick = {
                draft = WidgetRegistry.local(prompt, "w_" + UUID.randomUUID().toString().take(8)); revision = state.preferences.revision
                error = if (draft == null) "Try clock, battery, timer, counter, birthdays or calendar. This offline parser supports those registered types." else null
            }, enabled = prompt.isNotBlank(), modifier = Modifier.testTag("preview_widget")) { Text("Preview offline widget") }
            Text("Offline templates support the registered types. Nemotron can select a type, shape and duration from your description. Generated widgets run only registered components and actions.", color = style.muted, fontSize = 11.sp)
            TextButton(onClick = { connection = !connection }) { Text(if (state.preferences.localAiMode == "local") "Generate on this phone" else "Generate with AI") }
            if (connection) {
                if (state.preferences.localAiMode != "local") {
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS backend URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it }, label = { Text("MorphOS client token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Button(onClick = { model.proposeWidget(prompt, endpoint, token) }, enabled = prompt.isNotBlank() && !busy) { Text(if (busy) "Designing…" else "Preview AI widget") }
                inferenceError?.let { Text(it, color = style.muted) }
                ai?.let { Text("${it.explanation}\n${if (it.source == "device") "On-device" else "Nebius"} · ${it.model}", color = style.muted, fontSize = 12.sp) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            draft?.let { w ->
                WidgetCard(w, style, {}, Modifier.width(200.dp))
                Button(onClick = { model.addWidget(w, revision); draft = null }, enabled = state.preferences.widgets.size < 8 && revision == state.preferences.revision, modifier = Modifier.testTag("add_widget")) { Text("Add to home") }
                if (state.preferences.widgets.size >= 8) Text("Home supports eight widgets. Remove one to add another.", color = style.muted)
            }
            Text("On your home", color = style.text, fontSize = 20.sp)
            Text("Edit home to place and resize widgets. These controls change style and the widget collection order.", color = style.muted, fontSize = 12.sp)
            state.preferences.widgets.forEach { w ->
                Surface(color = style.surface, shape = style.shape) {
                    Column(Modifier.fillMaxWidth().padding(16.dp).pointerInput(w.id) {
                        var travel = 0f
                        detectDragGesturesAfterLongPress(onDragStart = { travel = 0f }, onDrag = { change, amount -> change.consume(); travel += amount.y }, onDragEnd = { if (kotlin.math.abs(travel) > 60) model.moveWidget(w.id, if (travel > 0) 1 else -1) })
                    }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(w.title, color = style.text)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WidgetShape.entries.forEach { shape -> FilterChip(w.shape == shape, { model.updateWidget(w.copy(shape = shape)) }, label = { Text(shape.name.lowercase()) }) }
                        }
                        Text("Rotation ${w.rotation.toInt()}°", color = style.muted, fontSize = 11.sp)
                        Slider(w.rotation, { model.updateWidget(w.copy(rotation = it)) }, valueRange = -30f..30f, steps = 3)
                        Text("Size ${(w.scale * 100).toInt()}%", color = style.muted, fontSize = 11.sp)
                        Slider(w.scale, { model.updateWidget(w.copy(scale = it)) }, valueRange = .75f..1.25f, steps = 3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { model.moveWidget(w.id, -1) }) { Text("Move up") }
                            TextButton(onClick = { model.moveWidget(w.id, 1) }) { Text("Move down") }
                            if (state.preferences.homeItems?.none { it.kind == HomeKind.WIDGET && it.target == w.id } == true) TextButton(onClick = { model.restoreWidget(w.id) }) { Text("Place on home") }
                            TextButton(onClick = { model.removeWidget(w.id) }) { Text("Remove") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
