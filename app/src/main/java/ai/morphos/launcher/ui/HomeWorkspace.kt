@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package ai.morphos.launcher.ui

import ai.morphos.launcher.domain.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Composable internal fun PhoneHome(state: LauncherState, model: LauncherViewModel, style: Style, isDefault: Boolean, requestHome: () -> Unit, settings: () -> Unit, themes: () -> Unit, drawer: () -> Unit, studio: () -> Unit, widgets: () -> Unit, search: () -> Unit, controls: () -> Unit) {
    LaunchedEffect(state.ready, state.preferences.homeItems) { if (state.ready && state.preferences.homeItems == null) model.ensureWorkspace() }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<HomeItem?>(null) }
    var tools by remember { mutableStateOf(false) }
    val pager = rememberPagerState(pageCount = { state.preferences.homePages })
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val items = state.preferences.homeItems.orEmpty()
    val oxygen = style.oxygen
    fun edit() { editing = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    fun page(index: Int) { scope.launch { if (style.spec.reducedMotion) pager.scrollToPage(index) else pager.animateScrollToPage(index) } }
    Box(Modifier.fillMaxSize().testTag("phone_home")) {
        WallpaperArt(style, Modifier.pointerInput(state.preferences.doubleTapAction) {
            detectTapGestures(onLongPress = { edit() }, onDoubleTap = { when (state.preferences.doubleTapAction) { "controls" -> controls(); "search" -> search() } })
        })
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    Text("Edit home", color = style.text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { model.homePage(true, pager.currentPage) }, enabled = state.preferences.homePages < Workspace.maxPages, modifier = Modifier.testTag("add_home_page")) { Text("+ Page") }
                    TextButton(onClick = { editing = false; selected = null }, modifier = Modifier.testTag("done_home_edit")) { Text("Done") }
                } else {
                    Text(style.spec.name, color = style.text.copy(alpha = .7f), fontSize = 11.sp, modifier = Modifier.weight(1f).testTag("home_theme_name"))
                    IconButton(onClick = controls, modifier = Modifier.testTag("controls")) { Icon(Icons.Outlined.Menu, "Control center", tint = style.text, modifier = Modifier.size(19.dp)) }
                    Box {
                        IconButton(onClick = { tools = true }, modifier = Modifier.testTag("home_tools")) { Icon(Icons.Outlined.MoreVert, "Home options", tint = style.text) }
                        DropdownMenu(tools, { tools = false }) {
                            DropdownMenuItem(text = { Text("Edit home pages") }, onClick = { tools = false; edit() }, modifier = Modifier.testTag("edit_home"))
                            DropdownMenuItem(text = { Text("Add widgets") }, onClick = { tools = false; widgets() }, modifier = Modifier.testTag("widgets"))
                            DropdownMenuItem(text = { Text("Themes & wallpaper") }, onClick = { tools = false; themes() }, modifier = Modifier.testTag("themes"))
                            DropdownMenuItem(text = { Text("AI design studio") }, onClick = { tools = false; studio() }, modifier = Modifier.testTag("studio_menu"))
                            DropdownMenuItem(text = { Text("Launcher settings") }, onClick = { tools = false; settings() }, modifier = Modifier.testTag("settings"))
                        }
                    }
                }
            }
            if (!isDefault) TextButton(onClick = requestHome) { Text("Set as your home launcher") }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("home_pager").pointerInput(editing) {
                var travel = 0f
                detectVerticalDragGestures(onDragStart = { travel = 0f }, onVerticalDrag = { change, delta -> if (!editing) { change.consume(); travel += delta } }, onDragEnd = { if (!editing) { if (travel < -80) drawer() else if (travel > 80) controls() } })
            }, beyondViewportPageCount = 1) { index ->
                Column(Modifier.fillMaxSize()) {
                    if (oxygen && index == 0) CompactHomeClock(style)
                    if (oxygen && index == 1) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Shelf", color = style.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = widgets) { Text("Add widgets") }
                    }
                    WorkspacePage(index, items.filter { it.page == index }, state, model, style, editing, { selected = it; edit() }, Modifier.weight(1f), onEmptyLongPress = { edit() }, onDoubleTap = { when (state.preferences.doubleTapAction) { "controls" -> controls(); "search" -> search() } })
                }
            }
            if (editing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = widgets, modifier = Modifier.testTag("widgets")) { Text("+ Widget") }
                    TextButton(onClick = drawer, modifier = Modifier.testTag("all_apps")) { Text("+ App") }
                    TextButton(onClick = { model.homePage(false, pager.currentPage) }, enabled = state.preferences.homePages > 1 && items.none { it.page == pager.currentPage }) { Text("Remove page") }
                }
                Text("Hold and drag. Drop at an edge to move between pages.", fontSize = 10.sp, color = style.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(state.preferences.homePages) { i ->
                    Box(Modifier.size(28.dp).clickable { page(i) }.semantics { contentDescription = "Home page ${i + 1}"; role = Role.Button; this.selected = pager.currentPage == i }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(if (pager.currentPage == i) 6.dp else 4.dp).background(if (pager.currentPage == i) style.text else style.text.copy(alpha = .35f), CircleShape))
                    }
                }
                IconButton(onClick = drawer, modifier = Modifier.size(32.dp).testTag("all_apps")) { Icon(Icons.Outlined.KeyboardArrowUp, "All apps", tint = style.text, modifier = Modifier.size(20.dp)) }
            }
            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                state.preferences.dockApps.mapNotNull { id -> state.apps.firstOrNull { it.id == id } }.forEach { app ->
                    Box(Modifier.weight(1f)) { AppTile(app, model, style, app.id in state.preferences.pinned, dock = true) }
                }
                if (state.preferences.dockApps.isEmpty()) TextButton(onClick = drawer) { Text("Add dock apps") }
            }
            Surface(onClick = search, shape = CircleShape, color = if (oxygen) style.surface.copy(alpha = .18f) else style.surface.copy(alpha = .85f), modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp).height(44.dp).testTag("search_home").pointerInput(Unit) {
                var distance = 0f
                detectVerticalDragGestures(onDragStart = { distance = 0f }, onVerticalDrag = { change, d -> change.consume(); distance += d }, onDragEnd = { if (distance < -35) drawer() else if (distance > 35) controls() })
            }) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = style.text, modifier = Modifier.size(20.dp))
                    Text("Search", color = style.text.copy(alpha = .7f), fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    IconButton(onClick = studio, modifier = Modifier.size(40.dp).testTag("studio")) { Icon(Icons.Outlined.Star, "AI design studio", tint = style.text, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
    selected?.let { original ->
        val item = items.firstOrNull { it.key == original.key }
        if (item != null) AlertDialog(onDismissRequest = { selected = null }, title = { Text(if (item.kind == HomeKind.WIDGET) "Place widget" else "Place app") }, text = {
            Column {
                Text("Page ${item.page + 1} · column ${item.column + 1} · row ${item.row + 1}")
                Row {
                    TextButton(onClick = { model.moveHome(item.key, (item.page - 1).coerceAtLeast(0), item.column, item.row) }, enabled = item.page > 0) { Text("Previous page") }
                    TextButton(onClick = { model.moveHome(item.key, item.page + 1, item.column, item.row) }, enabled = item.page + 1 < state.preferences.homePages) { Text("Next page") }
                }
                Row { listOf("←" to (-1 to 0), "↑" to (0 to -1), "↓" to (0 to 1), "→" to (1 to 0)).forEach { (label, delta) ->
                    TextButton(onClick = { model.moveHome(item.key, item.page, item.column + delta.first, item.row + delta.second) }) { Text(label) }
                } }
                if (item.kind == HomeKind.WIDGET) Row { listOf(2 to 1, 2 to 2, 4 to 2).forEach { (w,h) -> TextButton(onClick = { model.resizeHome(item.key, w, h) }) { Text("$w × $h") } } }
                TextButton(onClick = { model.removeHome(item.key); selected = null }) { Text("Remove from home") }
                if (item.kind == HomeKind.APP) state.apps.firstOrNull { it.id == item.target }?.let { app -> TextButton(onClick = { model.dock(app); selected = null }) { Text(if (item.target in state.preferences.dockApps) "Remove from dock" else "Add to dock") } }
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Done") } })
    }
}

@Composable private fun WorkspacePage(page: Int, items: List<HomeItem>, state: LauncherState, model: LauncherViewModel, style: Style, editing: Boolean, select: (HomeItem) -> Unit, modifier: Modifier, onEmptyLongPress: () -> Unit, onDoubleTap: () -> Unit) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxWidth().pointerInput(editing) {
        detectTapGestures(onLongPress = { onEmptyLongPress() }, onDoubleTap = { onDoubleTap() })
    }.testTag("workspace_page_$page")) {
        val cellWidth = maxWidth / Workspace.columns
        val cellHeight = maxHeight / Workspace.rows
        val pxW = with(density) { cellWidth.toPx() }; val pxH = with(density) { cellHeight.toPx() }
        if (editing) repeat(Workspace.rows) { row -> repeat(Workspace.columns) { col ->
            Box(Modifier.offset(cellWidth * col, cellHeight * row).size(cellWidth, cellHeight), contentAlignment = Alignment.Center) { Box(Modifier.size(3.dp).background(style.text.copy(alpha = .25f), CircleShape)) }
        } }
        if (items.isEmpty()) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { Text("Room for your ideas", color = style.text.copy(alpha = .5f), fontSize = 16.sp); Text("Hold to add apps and widgets", color = style.muted, fontSize = 11.sp) }
        items.forEach { item -> key(item.key) {
            var drag by remember { mutableStateOf(Offset.Zero) }; var dragging by remember { mutableStateOf(false) }
            val position by animateIntOffsetAsState(IntOffset(with(density) { (cellWidth * item.column).roundToPx() }, with(density) { (cellHeight * item.row).roundToPx() }), animationSpec = if (style.spec.reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow), label = "Home placement")
            Box(Modifier.offset { position }.size(cellWidth * item.width, cellHeight * item.height).padding(5.dp).zIndex(if (dragging) 2f else 0f)
                .graphicsLayer { translationX = drag.x; translationY = drag.y; scaleX = if (dragging) 1.04f else 1f; scaleY = scaleX }
                .pointerInput(editing, item, pxW, pxH) {
                    if (editing) detectDragGesturesAfterLongPress(onDragStart = { dragging = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }, onDrag = { change, delta -> change.consume(); drag += delta }, onDragCancel = { drag = Offset.Zero; dragging = false }, onDragEnd = {
                        val rawCol = item.column + (drag.x / pxW).roundToInt()
                        val targetPage = when { rawCol < 0 -> (page - 1).coerceAtLeast(0); rawCol + item.width > 4 -> (page + 1).coerceAtMost(state.preferences.homePages - 1); else -> page }
                        val col = when { targetPage < page -> 4 - item.width; targetPage > page -> 0; else -> rawCol.coerceIn(0, 4 - item.width) }
                        model.moveHome(item.key, targetPage, col, (item.row + (drag.y / pxH).roundToInt()).coerceIn(0, 6 - item.height))
                        drag = Offset.Zero; dragging = false
                    })
                }.testTag("home_item_${item.target}"), contentAlignment = Alignment.Center) {
                when (item.kind) {
                    HomeKind.APP -> state.apps.firstOrNull { it.id == item.target }?.let { AppTile(it, model, style, true, compact = true, interactive = !editing) }
                    HomeKind.WIDGET -> state.preferences.widgets.firstOrNull { it.id == item.target }?.let { WidgetCard(it, style, { model.widgetAction(it.id) }, Modifier.fillMaxSize(), fit = true, compact = item.height == 1) }
                }
                if (editing) Box(Modifier.fillMaxSize().border(1.dp, style.text.copy(alpha = .3f), RoundedCornerShape(20.dp)).clickable { select(item) }.semantics { contentDescription = "Arrange ${if (item.kind == HomeKind.APP) state.apps.firstOrNull { it.id == item.target }?.label ?: "app" else state.preferences.widgets.firstOrNull { it.id == item.target }?.title ?: "widget"}"; role = Role.Button })
            }
        } }
    }
}

@Composable private fun CompactHomeClock(style: Style) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); kotlinx.coroutines.delay(15000) } }
    Column(Modifier.padding(top = 8.dp, start = 4.dp, bottom = 12.dp)) {
        Text(now.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }, color = style.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), color = style.text, fontSize = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
        Text(now.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")), color = style.text.copy(alpha = .7f), fontSize = 12.sp)
    }
}
