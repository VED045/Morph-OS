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
        Header("Control center", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")), style, back)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlTile("Wi-Fi", "Internet panel", style, Modifier.weight(1f)) { launch(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) }
                ControlTile("Bluetooth", "Connection settings", style, Modifier.weight(1f)) { launch(Settings.ACTION_BLUETOOTH_SETTINGS) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlTile(if (battery.first < 0) "Battery" else "${battery.first}%", if (battery.second) "Charging" else "Battery settings", style, Modifier.weight(1f)) { launch(Settings.ACTION_BATTERY_SAVER_SETTINGS) }
                ControlTile("Notifications", "Private dashboard", style, Modifier.weight(1f).testTag("notification_dashboard"), notifications)
            }
            Surface(shape = RoundedCornerShape(32.dp), color = if (style.oxygen) style.bg.copy(alpha = .65f) else style.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Media volume", color = style.text)
                    Slider(volume, { volume = it }, onValueChangeFinished = { runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume.toInt(), 0) } }, valueRange = 0f..maxVolume.toFloat())
                    Text("MorphOS screen brightness", color = style.text)
                    Slider(brightness, { brightness = it; (context as? ComponentActivity)?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = it } } }, valueRange = .1f..1f)
                    TextButton(onClick = { launch(Settings.ACTION_DISPLAY_SETTINGS) }) { Text("System display settings") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Display" to Settings.ACTION_DISPLAY_SETTINGS, "Sound" to Settings.ACTION_SOUND_SETTINGS, "Battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS).forEach { (label, action) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(onClick = { launch(action) }, shape = CircleShape, color = if (style.nothing) style.surface else style.accent.copy(alpha = .65f), modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { ControlGlyph(label, style.text) } }
                        Text(label, color = style.muted, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
            MediaCard(style)
            Surface(shape = style.shape, color = if (style.oxygen) style.bg.copy(alpha = .65f) else style.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Floating panel", color = style.text)
                    Text("A movable battery and notification panel. Close it at any time; Android may remove it when MorphOS is stopped.", color = style.muted, fontSize = 12.sp)
                    OutlinedButton(onClick = {
                        if (Settings.canDrawOverlays(context)) (context as? ComponentActivity)?.let { FloatingPanel.show(it, style) }
                        else runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                    }) { Text(if (Settings.canDrawOverlays(context)) "Show floating panel" else "Manage overlay access") }
                }
            }
            Surface(shape = RoundedCornerShape(32.dp), color = if (style.oxygen) style.bg.copy(alpha = .65f) else style.surface) {
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
    Surface(onClick = action, shape = RoundedCornerShape(if (style.nothing) 20.dp else 28.dp), color = if (style.oxygen) style.bg.copy(alpha = .65f) else style.surface, modifier = modifier.height(96.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(if (style.nothing) style.text.copy(alpha = .08f) else style.accent.copy(alpha = .35f), CircleShape), contentAlignment = Alignment.Center) { ControlGlyph(if (title.endsWith("%")) "Battery" else title, style.text) }
            Column(Modifier.weight(1f)) { Text(title, color = style.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1); Text(subtitle, color = style.muted, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2) }
        }
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
    Surface(shape = RoundedCornerShape(32.dp), color = if (style.oxygen) style.bg.copy(alpha = .65f) else style.surface) {
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

@Composable private fun ControlGlyph(label: String, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(26.dp)) {
        val w = size.width; val h = size.height; val line = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        when (label) {
            "Wi-Fi" -> { for (fraction in listOf(.90f,.62f,.34f)) { drawArc(color, 225f, 90f, false, androidx.compose.ui.geometry.Offset(w * (1-fraction)/2, h*(1-fraction)/2), androidx.compose.ui.geometry.Size(w*fraction,h*fraction), style=line) }; drawCircle(color,w*.07f,androidx.compose.ui.geometry.Offset(w*.5f,h*.72f)) }
            "Bluetooth" -> { val path=androidx.compose.ui.graphics.Path().apply { moveTo(w*.5f,h*.1f); lineTo(w*.5f,h*.9f); lineTo(w*.78f,h*.68f); lineTo(w*.23f,h*.28f); moveTo(w*.5f,h*.1f); lineTo(w*.78f,h*.32f); lineTo(w*.23f,h*.72f) }; drawPath(path,color,style=line) }
            "Notifications" -> { val path=androidx.compose.ui.graphics.Path().apply { moveTo(w*.2f,h*.72f); lineTo(w*.29f,h*.58f); lineTo(w*.29f,h*.36f); cubicTo(w*.29f,h*.1f,w*.71f,h*.1f,w*.71f,h*.36f); lineTo(w*.71f,h*.58f); lineTo(w*.8f,h*.72f); close() }; drawPath(path,color,style=line); drawCircle(color,w*.07f,androidx.compose.ui.geometry.Offset(w*.5f,h*.85f)) }

            "Display" -> { drawCircle(color, w * .21f, center, style = line); repeat(8) { i -> val a = i * Math.PI / 4; val p = androidx.compose.ui.geometry.Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat()); drawLine(color, center + p * (w * .34f), center + p * (w * .46f), 2.dp.toPx()) } }
            "Battery" -> { drawRoundRect(color, androidx.compose.ui.geometry.Offset(w * .25f, h * .16f), androidx.compose.ui.geometry.Size(w * .5f, h * .74f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = line); drawLine(color, androidx.compose.ui.geometry.Offset(w * .42f, h * .05f), androidx.compose.ui.geometry.Offset(w * .58f, h * .05f), 2.dp.toPx()); drawRect(color, androidx.compose.ui.geometry.Offset(w * .34f, h * .5f), androidx.compose.ui.geometry.Size(w * .32f, h * .3f)) }
            "Sound" -> { val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * .1f,h * .4f); lineTo(w * .3f,h * .4f); lineTo(w * .53f,h * .2f); lineTo(w * .53f,h * .8f); lineTo(w * .3f,h * .6f); lineTo(w * .1f,h * .6f); close() }; drawPath(path,color,style=line); drawArc(color,-55f,110f,false,androidx.compose.ui.geometry.Offset(w * .42f,h * .2f),androidx.compose.ui.geometry.Size(w * .45f,h * .6f),style=line) }
            else -> { val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * .5f,h * .08f); lineTo(w * .58f,h * .42f); lineTo(w * .93f,h * .62f); lineTo(w * .93f,h * .73f); lineTo(w * .58f,h * .61f); lineTo(w * .56f,h * .84f); lineTo(w * .69f,h * .94f); lineTo(w * .31f,h * .94f); lineTo(w * .44f,h * .84f); lineTo(w * .42f,h * .61f); lineTo(w * .07f,h * .73f); lineTo(w * .07f,h * .62f); lineTo(w * .42f,h * .42f); close() }; drawPath(path,color) }
        }
    }
}
