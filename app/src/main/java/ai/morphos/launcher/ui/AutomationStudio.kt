package ai.morphos.launcher.ui
import ai.morphos.launcher.domain.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

@Composable internal fun AutomationStudio(state: LauncherState, model: LauncherViewModel, style: Style, back: () -> Unit) {
    var trigger by rememberSaveable { mutableStateOf(Trigger.TIME_WINDOW) }
    var themeId by rememberSaveable { mutableStateOf("amoled") }
    var start by rememberSaveable { mutableFloatStateOf(22f) }
    var end by rememberSaveable { mutableFloatStateOf(6f) }
    var threshold by rememberSaveable { mutableFloatStateOf(20f) }
    var weekdays by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Header("Let your home adapt", "Rules you control · Undoable themes", style, back)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create a rule", color = style.text, fontSize = 24.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Trigger.entries.forEach { t -> FilterChip(trigger == t, { trigger = t }, label = { Text(t.name.lowercase().replace('_', ' ')) }) } }
            if (trigger == Trigger.TIME_WINDOW) {
                Text("From ${start.toInt()}:00 to ${end.toInt()}:00", color = style.muted)
                Slider(start, { start = it }, valueRange = 0f..23f, steps = 22)
                Slider(end, { end = it }, valueRange = 0f..23f, steps = 22)
                Row { Checkbox(weekdays, { weekdays = it }); Text("Weekdays only", color = style.text, modifier = Modifier.padding(top = 14.dp)) }
            }
            if (trigger == Trigger.BATTERY_BELOW) { Text("Below ${threshold.toInt()}% battery", color = style.muted); Slider(threshold, { threshold = it }, valueRange = 5f..95f, steps = 17) }
            Text("Switch to", color = style.text)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { model.themes.themes.forEach { theme -> FilterChip(theme.id == themeId, { themeId = theme.id }, label = { Text(theme.name) }) } }
            Button(onClick = { model.addRule(AutomationRule("rule_" + UUID.randomUUID().toString().take(8), trigger, themeId, startHour = start.toInt(), endHour = end.toInt(), threshold = threshold.toInt(), weekdaysOnly = weekdays)) }, enabled = state.preferences.rules.size < 8 && start.toInt() != end.toInt()) { Text("Add rule · Starts disabled") }
            Text("Enable a rule below to approve automatic theme changes. WorkManager checks approximately every 15 minutes, with possible Android/OEM delays. Theme changes force dark appearance; undo restores the previous design.", color = style.muted, fontSize = 12.sp)
            state.preferences.rules.forEach { rule ->
                Surface(shape = style.shape, color = style.surface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("${rule.trigger.name.lowercase().replace('_', ' ')} → ${model.themes.find(rule.themeId).name}", color = style.text, modifier = Modifier.weight(1f))
                            Switch(rule.enabled, { model.updateRule(rule.copy(enabled = it)) })
                        }
                        Text(when (rule.trigger) { Trigger.TIME_WINDOW -> "${rule.startHour}:00–${rule.endHour}:00"; Trigger.CHARGING -> "While charging"; Trigger.BATTERY_BELOW -> "Below ${rule.threshold}%" }, color = style.muted, fontSize = 12.sp)
                        TextButton(onClick = { model.removeRule(rule.id) }) { Text("Remove rule") }
                    }
                }
            }
            OutlinedButton(onClick = model::evaluateRules) { Text("Check enabled rules now") }
            TextButton(onClick = model::undo, enabled = state.preferences.canUndo) { Text("Undo last change") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
