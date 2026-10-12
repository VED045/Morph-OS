package ai.morphos.launcher.ui

import ai.morphos.launcher.data.*
import android.app.ActivityManager
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.*
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import android.net.Uri
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.*

@Composable internal fun ControlDashboard(style: Style, back: () -> Unit, notifications: () -> Unit) {
    val context = LocalContext.current
    val battery = rememberBattery()
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var brightness by remember { mutableFloatStateOf(.6f) }
    val memory = remember { ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) } }
    val storage = remember { StatFs(context.filesDir.absolutePath) }
    fun launch(action: String) { runCatching { context.startActivity(Intent(action)) } }
    DisposableEffect(context) {
        val window = (context as? ComponentActivity)?.window
        val old = window?.attributes?.screenBrightness ?: -1f
        onDispose { window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = old } } }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Header("Control center", "Your device, at a glance", style, back)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Stay connected.", color = style.text, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlTile("Wi-Fi", "Internet panel", style, Modifier.weight(1f)) { launch(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) }
                ControlTile("Bluetooth", "Connection settings", style, Modifier.weight(1f)) { launch(Settings.ACTION_BLUETOOTH_SETTINGS) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlTile(if (battery.first < 0) "Battery" else "${battery.first}%", if (battery.second) "Charging" else "Battery settings", style, Modifier.weight(1f)) { launch(Settings.ACTION_BATTERY_SAVER_SETTINGS) }
                ControlTile("Notifications", "Private dashboard", style, Modifier.weight(1f).testTag("notification_dashboard"), notifications)
            }
            Surface(shape = RoundedCornerShape(32.dp), color = style.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Media volume", color = style.text)
                    Slider(volume, { volume = it }, onValueChangeFinished = { runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume.toInt(), 0) } }, valueRange = 0f..maxVolume.toFloat())
                    Text("MorphOS screen brightness", color = style.text)
                    Slider(brightness, { brightness = it; (context as? ComponentActivity)?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = it } } }, valueRange = .1f..1f)
                    TextButton(onClick = { launch(Settings.ACTION_DISPLAY_SETTINGS) }) { Text("System display settings") }
                }
            }
            MediaCard(style)
            Surface(shape = style.shape, color = style.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Floating panel", color = style.text)
                    Text("A movable battery and notification panel. Close it at any time; Android may remove it when MorphOS is stopped.", color = style.muted, fontSize = 12.sp)
                    OutlinedButton(onClick = {
                        if (Settings.canDrawOverlays(context)) (context as? ComponentActivity)?.let { FloatingPanel.show(it, style) }
                        else runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                    }) { Text(if (Settings.canDrawOverlays(context)) "Show floating panel" else "Manage overlay access") }
                }
            }
            Surface(shape = RoundedCornerShape(32.dp), color = style.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(Build.MODEL, color = style.text, fontSize = 22.sp)
                    Text("Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER}", color = style.muted)
                    Text("Storage · ${storage.availableBytes / 1073741824L} GB free / ${storage.totalBytes / 1073741824L} GB", color = style.muted)
                    Text("Memory · ${memory.availMem / 1048576L} MB available", color = style.muted)
                    OutlinedButton(onClick = { launch(Settings.ACTION_SETTINGS) }) { Text("Android settings") }
                }
            }
            Text("Connection tiles open supported Android panels. Screen brightness changes apply to MorphOS while this dashboard is open.", color = style.muted, fontSize = 11.sp)
            Spacer(Modifier.height(20.dp))
        }
    }
}
@Composable private fun ControlTile(title: String, subtitle: String, style: Style, modifier: Modifier, action: () -> Unit) {
    Surface(onClick = action, shape = RoundedCornerShape(30.dp), color = style.surface, modifier = modifier.height(120.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) { Text(title, color = style.text, fontSize = 22.sp); Text(subtitle, color = style.muted, fontSize = 11.sp) }
    }
}
@Composable internal fun MediaCard(style: Style) {
    val context = LocalContext.current
    val connected by NotificationRepository.connected.collectAsStateWithLifecycle()
    var controllers by remember { mutableStateOf<List<MediaController>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(connected) {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers = it.orEmpty() }
        if (NotificationRepository.allowed(context)) runCatching {
            controllers = manager.getActiveSessions(NotificationRepository.component(context))
            manager.addOnActiveSessionsChangedListener(listener, NotificationRepository.component(context), Handler(Looper.getMainLooper()))
        }
        onDispose { runCatching { manager.removeOnActiveSessionsChangedListener(listener) }; controllers = emptyList() }
    }
    val controller = controllers.firstOrNull()
    DisposableEffect(controller) {
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { revision++ }
            override fun onPlaybackStateChanged(state: PlaybackState?) { revision++ }
        }
        controller?.registerCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { controller?.unregisterCallback(callback) }
    }
    Surface(shape = RoundedCornerShape(32.dp), color = style.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (controller == null) { Text("A little quiet", color = style.text, fontSize = 22.sp); Text(if (connected) "Start playback in your music app." else "Connect notification access to show media sessions.", color = style.muted, fontSize = 12.sp) }
            else {
                val metadata = remember(controller, revision) { controller.metadata }
                val playback = remember(controller, revision) { controller.playbackState }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    if (art != null) Image(art.asImageBitmap(), "Album artwork", Modifier.size(56.dp))
                    Column(Modifier.padding(start = if (art != null) 12.dp else 0.dp)) {
                        Text(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: controller.packageName, color = style.text, maxLines = 2)
                        Text(metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Media session", color = style.muted, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { runCatching { controller.transportControls.skipToPrevious() } }, enabled = ((playback?.actions ?: 0) and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L) { Text("Previous") }
                    Button(onClick = { runCatching { if (playback?.state == PlaybackState.STATE_PLAYING) controller.transportControls.pause() else controller.transportControls.play() } }) { Text(if (playback?.state == PlaybackState.STATE_PLAYING) "Pause" else "Play") }
                    OutlinedButton(onClick = { runCatching { controller.transportControls.skipToNext() } }, enabled = ((playback?.actions ?: 0) and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L) { Text("Next") }
                }
            }
        }
    }
}
