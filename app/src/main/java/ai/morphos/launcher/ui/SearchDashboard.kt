package ai.morphos.launcher.ui

import ai.morphos.launcher.domain.*
import ai.morphos.launcher.data.WidgetBindings
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*

@Composable internal fun SearchDashboard(state: LauncherState, model: LauncherViewModel, style: Style, back: () -> Unit, navigate: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val contacts by model.contacts.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permitted by remember { mutableStateOf(WidgetBindings.allowed(context, Manifest.permission.READ_CONTACTS)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it; model.refreshContacts() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { permitted = WidgetBindings.allowed(context, Manifest.permission.READ_CONTACTS); model.refreshContacts() } }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val appResults = remember(query, state.apps) { AppSearch.filter(state.apps, query).take(12) }
    val people = if (permitted) ContactRanking.search(contacts, query, history, state.preferences.contactPins.toSet(), System.currentTimeMillis()).take(12) else emptyList()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Header("Search your space", "Apps · People · Themes · Widgets", style, back)
        OutlinedTextField(query, { query = it.take(200) }, label = { Text("Search locally") }, modifier = Modifier.fillMaxWidth().testTag("unified_search"), singleLine = true, shape = style.shape)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip({ navigate("studio") }, label = { Text("AI design") })
                    AssistChip({ navigate("widgets") }, label = { Text("Widget Studio") })
                    AssistChip({ navigate("settings") }, label = { Text("Settings") })
                }
            }
            item { Text("Applications", color = style.muted, fontSize = 12.sp) }
            items(appResults, key = { "app_" + it.id }) { app -> SearchRow(app.label, app.category, style) { model.launch(app) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("People · On your phone", color = style.muted, fontSize = 12.sp)
                    if (!permitted) TextButton(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) { Text("Connect contacts") }
                }
            }
            if (permitted && people.isEmpty()) item { Text("No matching contacts", color = style.muted) }
            items(people, key = { "contact_" + it.contact.key }) { result ->
                Surface(color = style.surface, shape = style.shape) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).heightIn(min = 56.dp).clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.contact.uri))); model.recordContact(result.contact) }
                        }.padding(vertical = 10.dp)) { Text(result.contact.name, color = style.text); Text(result.reason, color = style.muted, fontSize = 11.sp) }
                        TextButton(onClick = { model.pinContact(result.contact) }) { Text(if (result.contact.key in state.preferences.contactPins) "Unpin" else "Pin") }
                    }
                }
            }
            item { Text("Themes", color = style.muted, fontSize = 12.sp) }
            items(model.themes.themes.filter { query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) }.take(6), key = { "theme_" + it.id }) { theme -> SearchRow(theme.name, "Apply offline theme", style) { model.theme(theme.id); back() } }
            item { Text("Widgets", color = style.muted, fontSize = 12.sp) }
            items(state.preferences.widgets.filter { query.isBlank() || it.title.contains(query, true) }, key = { "widget_" + it.id }) { w -> SearchRow(w.title, "${w.kind.name.lowercase()} · Edit in Widget Studio", style) { navigate("widgets") } }
        }
    }
}
@Composable internal fun SearchRow(title: String, subtitle: String, style: Style, action: () -> Unit) {
    Surface(onClick = action, color = style.surface, shape = style.shape, modifier = Modifier.fillMaxWidth().testTag("search_result_$title")) {
        Column(Modifier.padding(16.dp)) { Text(title, color = style.text); Text(subtitle, color = style.muted, fontSize = 11.sp) }
    }
}
