package ai.morphos.launcher.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable internal fun GlanceScreen(style: Style, back: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(15000) } }
    Box(Modifier.fillMaxSize()) {
        WallpaperArt(style)
        Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            TextButton(onClick = back) { Text("Return home", color = style.text) }
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = style.muted)
            DotTime(now.format(DateTimeFormatter.ofPattern("HH:mm")), style.text)
            Text("Pause. Breathe. Begin again.", color = style.text, fontSize = 22.sp)
            MediaCard(style)
            Text("Glance is a MorphOS screen. Your secure Android lock screen stays in control.", color = style.muted, fontSize = 12.sp)
        }
    }
}
