package ai.morphos.launcher.ui

import ai.morphos.launcher.data.*
import android.content.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import kotlinx.coroutines.*

@Composable internal fun ClipboardBridge(sync: ClipboardSync) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    DisposableEffect(sync, lifecycle) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && (context as? android.app.Activity)?.hasWindowFocus() == true) scope.launch {
                try { sync.clipboardChanged() } catch (e: CancellationException) { throw e } catch (_: Exception) { sync.status.value = "Could not send clipboard. Check your pairing." }
            }
        }
        clipboard.addPrimaryClipChangedListener(listener)
        onDispose { clipboard.removePrimaryClipChangedListener(listener) }
    }
    LaunchedEffect(sync, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                if (sync.paired() && sync.enabled.value && (context as? android.app.Activity)?.hasWindowFocus() == true) {
                    try { sync.poll() } catch (e: CancellationException) { throw e } catch (_: Exception) { sync.status.value = "Laptop unavailable. Reconnecting while MorphOS is open…" }
                }
                delay(2500)
            }
        }
    }
}

@Composable internal fun ClipboardScreen(sync: ClipboardSync, style: Style, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf(sync.address()) }
    var pin by rememberSaveable { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pairing by rememberSaveable { mutableStateOf(!sync.paired()) }
    val status by sync.status.collectAsStateWithLifecycle()
    val enabled by sync.enabled.collectAsStateWithLifecycle()
    val clips by sync.clips.collectAsStateWithLifecycle()
    val incoming by sync.incoming.collectAsStateWithLifecycle()
    fun action(block: suspend () -> Unit) { scope.launch { busy = true; try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { sync.status.value = "Transfer failed. Check pairing, network and the 100 MB file limit." } finally { busy = false } } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> action { require(uris.size <= 10); uris.forEach { sync.sendUri(it) } } }
    var saving by remember { mutableStateOf<SharedClip?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> val item = saving; if (uri != null && item?.file != null) action {
        withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).use { output -> item.file.inputStream().use { it.copyTo(output) } } }
        sync.status.value = "Saved to the selected folder"
    } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Header("Connected clipboard", "Your phone ↔ your Windows laptop", style, back)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Drop something. Pick it up anywhere.", color = style.text, fontSize = 28.sp)
            Text("Images, videos and text over your paired local network. Shared files stay in app cache until cleared, evicted or removed by Android.", color = style.muted, fontSize = 13.sp)
            if (pairing) {
                Text("Run companion/start.ps1 on Windows. Copy the address, certificate fingerprint and pairing key from its local window.", color = style.muted, fontSize = 12.sp)
                OutlinedTextField(host, { host = it.take(300) }, label = { Text("https://laptop-ip:8767") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(pin, { pin = it.take(100) }, label = { Text("Certificate SHA-256") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(key, { key = it.take(100) }, label = { Text("Pairing key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button({ action { sync.pair(host, pin, key); key = ""; pairing = false } }, enabled = !busy && pin.isNotBlank() && key.isNotBlank()) { Text("Pair laptop") }
            } else {
                Surface(color = style.surface, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(host, color = style.text)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("Sync while MorphOS is open", color = style.text, modifier = Modifier.weight(1f)); Switch(enabled, { sync.enabled.value = it }) }
                        Text("Android limits clipboard access to the focused app. In other apps, use Share → MorphOS. Sensitive clips marked by their source app are excluded from automatic sending.", color = style.muted, fontSize = 12.sp)
                    }
                }
                incoming?.let { clip ->
                    Text("${clip.itemCount} shared item(s) ready to send", color = style.text)
                    Row { Button({ action { sync.sendClip(clip); sync.incoming.value = null } }, enabled = !busy) { Text("Send shared items") }; TextButton({ sync.incoming.value = null }) { Text("Discard") } }
                }
                OutlinedTextField(text, { text = it.take(32768) }, label = { Text("Text for your laptop") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ action { sync.sendText(text) } }, enabled = !busy && text.isNotBlank()) { Text("Send text") }
                    OutlinedButton({ picker.launch(arrayOf("image/*", "video/*")) }, enabled = !busy) { Text("Send media") }
                }
                OutlinedButton({ action { sync.poll(false) } }, enabled = !busy) { Text("Receive from laptop") }
                Text("Drag images, videos or text here in Android split screen, or use the share sheet. Transfers are encrypted to the certificate you paired.", color = style.muted, fontSize = 12.sp)
                clips.asReversed().forEach { item ->
                    Surface(shape = RoundedCornerShape(22.dp), color = style.surface) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(item.text?.take(300) ?: item.name, color = style.text)
                            Text(item.mime, color = style.muted, fontSize = 11.sp)
                            Row {
                                TextButton({ sync.copy(item) }) { Text("Copy") }
                                if (item.file != null) {
                                    TextButton({ saving = item; save.launch(item.name) }) { Text("Save") }
                                    TextButton({ runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = item.mime; putExtra(Intent.EXTRA_STREAM, sync.uri(item)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share received file")) } }) { Text("Share") }
                                }
                            }
                        }
                    }
                }
                Row { TextButton({ sync.clear() }) { Text("Clear received") }; TextButton({ sync.disconnect(); pairing = true }) { Text("Disconnect") } }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, color = style.muted, fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))
        }
    }
}
