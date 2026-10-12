package ai.morphos.launcher.ui

import ai.morphos.launcher.data.*
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.*

@Composable internal fun NotificationDashboard(style: Style, back: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permitted by remember { mutableStateOf(NotificationRepository.allowed(context)) }
    var historyEnabled by remember { mutableStateOf(NotificationRepository.historyEnabled(context)) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val connected by NotificationRepository.connected.collectAsStateWithLifecycle()
    val active by NotificationRepository.active.collectAsStateWithLifecycle()
    val history by NotificationRepository.history.collectAsStateWithLifecycle()
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { permitted = NotificationRepository.allowed(context); if (!permitted) NotificationRepository.disconnect() } }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Header("Your updates", "Local grouping · Private by default", style, back)
        if (!permitted) {
            Text("Connect your notifications", color = style.text, fontSize = 30.sp)
            Text("Android will ask you to authorize notification access. Contents stay on your phone; history is off until you enable it.", color = style.muted, modifier = Modifier.padding(vertical = 16.dp))
            Button(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }) { Text("Manage notification access") }
        } else {
            OutlinedTextField(query, { query = it.take(200) }, label = { Text("Search notifications") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = style.shape)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("Encrypted local history", color = style.text, modifier = Modifier.weight(1f)); Switch(historyEnabled, { historyEnabled = it; NotificationRepository.setHistory(context, it) }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showHistory, { showHistory = false }, label = { Text("Now") })
                FilterChip(showHistory, { showHistory = true }, label = { Text("History") }, enabled = historyEnabled)
                TextButton(onClick = { NotificationRepository.clearHistory(context) }) { Text("Delete history") }
            }
            val cards = (if (showHistory) history else active.map { it.card }).filter { it.title.contains(query, true) || it.text.contains(query, true) || it.packageName.contains(query, true) }
            if (!connected) Text("Waiting for Android to connect the listener…", color = style.muted)
            else if (cards.isEmpty()) Text("All caught up.", color = style.muted, modifier = Modifier.padding(24.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                cards.groupBy { if (it.category in setOf("msg", "email", "call")) "People" else if (it.category == "transport") "Media" else it.packageName }.toSortedMap().forEach { (group, entries) ->
                    item { Text(group, color = style.muted, fontSize = 12.sp) }
                    items(entries, key = { it.key }) { card ->
                        val source = active.firstOrNull { it.card.key == card.key }?.source
                        Surface(shape = style.shape, color = style.surface) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(card.title, color = style.text); Text(card.text, color = style.muted, fontSize = 13.sp)
                                if (!showHistory && source != null) {
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (source.notification.contentIntent != null) TextButton(onClick = { runCatching { source.notification.contentIntent.send() } }) { Text("Open") }
                                        source.notification.actions?.filter { it.remoteInputs.isNullOrEmpty() }?.take(3)?.forEach { action -> TextButton(onClick = { runCatching { action.actionIntent.send() } }) { Text(action.title.toString().take(30)) } }
                                        if (!card.ongoing) TextButton(onClick = { NotificationRepository.dismiss(card.key) }) { Text("Dismiss") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
