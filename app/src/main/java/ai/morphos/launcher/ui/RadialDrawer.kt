package ai.morphos.launcher.ui
import ai.morphos.launcher.domain.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import kotlin.math.*
import kotlinx.coroutines.launch

@Composable internal fun RadialDrawer(apps: List<AppEntry>, state: LauncherState, model: LauncherViewModel, style: Style) {
    val pages = apps.chunked(8)
    val pager = rememberPagerState(pageCount = { pages.size })
    Column {
        Text("Polar layout · Swipe pages · Use List for a linear view", color = style.muted, fontSize = 11.sp)
        HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f).testTag("radial_drawer")) { page ->
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val entries = pages[page]
                val radius = (minOf(maxWidth.value, maxHeight.value) / 2 - 46).coerceAtLeast(0f)
                entries.forEachIndexed { index, app ->
                    val angle = -PI / 2 + (index - 1) * 2 * PI / (entries.size - 1).coerceAtLeast(1)
                    val x = if (index == 0) 0.dp else (cos(angle) * radius).toFloat().dp
                    val y = if (index == 0) 0.dp else (sin(angle) * radius).toFloat().dp
                    Box(Modifier.offset(x, y).width(80.dp)) { AppTile(app, model, style, app.id in state.preferences.pinned) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val scope = rememberCoroutineScope()
            TextButton(onClick = { scope.launch { val target = (pager.currentPage - 1).coerceAtLeast(0); if (style.spec.reducedMotion) pager.scrollToPage(target) else pager.animateScrollToPage(target) } }, enabled = pager.currentPage > 0) { Text("Previous") }
            Text("${pager.currentPage + 1} / ${pages.size}", color = style.muted)
            TextButton(onClick = { scope.launch { val target = (pager.currentPage + 1).coerceAtMost(pages.lastIndex); if (style.spec.reducedMotion) pager.scrollToPage(target) else pager.animateScrollToPage(target) } }, enabled = pager.currentPage < pages.lastIndex) { Text("Next") }
        }
    }
}
