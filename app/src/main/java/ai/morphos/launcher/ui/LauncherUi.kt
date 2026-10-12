@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package ai.morphos.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.graphics.toColorInt
import ai.morphos.launcher.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal fun String.color() = Color(toColorInt())
internal data class Style(val spec: ThemeSpec, val palette: Palette) {
    val bg = palette.background.color()
    val surface = palette.surface.color()
    val text = palette.foreground.color()
    val muted = palette.muted.color()
    val accent = palette.accent.color()
    val shape = RoundedCornerShape(spec.cornerRadius.dp)
    val font = when (spec.typography) { "mono" -> FontFamily.Monospace; "serif" -> FontFamily.Serif; else -> FontFamily.SansSerif }
}

@Composable
fun LauncherRoot(model: LauncherViewModel, defaultHome: StateFlow<Boolean>, homeRequest: StateFlow<Int>, requestHome: () -> Unit, homeSettings: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val isDefault by defaultHome.collectAsStateWithLifecycle()
    val homeEvent by homeRequest.collectAsStateWithLifecycle()
    val catalog by model.themes.catalog.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("home") }
    var query by rememberSaveable { mutableStateOf("") }
    val spec = state.preferences.customTheme ?: catalog.firstOrNull { it.id == state.preferences.themeId } ?: FallbackTheme.value
    val dark = when (state.preferences.appearance) { Appearance.SYSTEM -> isSystemInDarkTheme(); Appearance.DARK -> true; Appearance.LIGHT -> false }
    val style = Style(spec, if (dark) spec.dark else spec.light)
    val context = LocalContext.current
    SideEffect {
        (context as? ComponentActivity)?.let { activity ->
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(0, 0) { dark },
                navigationBarStyle = SystemBarStyle.auto(style.bg.toArgb(), style.bg.toArgb()) { dark }
            )
        }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(homeEvent) { if (homeEvent > 0) { screen = "home"; query = "" } }
    LaunchedEffect(model) { model.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler { if (screen != "home") { screen = "home"; query = "" } }
    val baseScheme = if (dark) darkColorScheme(primary = style.accent, background = style.bg, surface = style.surface, onSurface = style.text, onBackground = style.text)
        else lightColorScheme(primary = style.accent, background = style.bg, surface = style.surface, onSurface = style.text, onBackground = style.text)
    val scheme = baseScheme.copy(secondary = style.accent, secondaryContainer = style.accent.copy(alpha = .15f).compositeOver(style.surface),
        onSecondaryContainer = style.text, surfaceVariant = style.surface, onSurfaceVariant = style.muted, outline = style.muted)
    MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = style.font))) {
        Scaffold(containerColor = style.bg, snackbarHost = { SnackbarHost(snackbar) }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).background(if (spec.gradient) Brush.verticalGradient(listOf(style.accent.copy(alpha = 0.09f).compositeOver(style.bg), style.bg)) else Brush.verticalGradient(listOf(style.bg, style.bg)))) {
                when (screen) {
                    "home" -> Home(state, model, style, isDefault, requestHome, { screen = "settings" }, { screen = "themes" }, { screen = "drawer"; query = "" }, { screen = "studio" }, { screen = "widgets" }, { screen = "search" }, { screen = "controls" })
                    "controls" -> ControlDashboard(style, { screen = "home" }, { screen = "notifications" })
                    "notifications" -> NotificationDashboard(style, { screen = "controls" })
                    "automation" -> AutomationStudio(state, model, style, { screen = "home" })
                    "glance" -> GlanceScreen(style, { screen = "home" })
                    "search" -> SearchDashboard(state, model, style, { screen = "home" }, { screen = it })
                    "widgets" -> WidgetStudio(state, model, style, { screen = "home" })
                    "studio" -> DesignStudio(state, model, style, dark, { screen = "home" })
                    "drawer" -> Drawer(state, model, style, query, { query = it }, { screen = "home"; query = "" })
                    "themes" -> Themes(model, style, dark, { screen = "home" })
                    "settings" -> Preferences(state, model, style, isDefault, requestHome, homeSettings, { screen = "home" }, { screen = "automation" }, { screen = "glance" })
                }
            }
        }
    }
}

@Composable
private fun Home(state: LauncherState, model: LauncherViewModel, style: Style, isDefault: Boolean, requestHome: () -> Unit, settings: () -> Unit, themes: () -> Unit, drawer: () -> Unit, studio: () -> Unit, widgets: () -> Unit, search: () -> Unit, controls: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000L * (60 - now.second)) } }
    val pins = state.preferences.pinned.mapNotNull { id -> state.apps.find { it.id == id } }
    val visible = (pins + state.apps.filterNot { app -> pins.any { it.id == app.id } }).take(if (pins.isEmpty()) 16 else maxOf(8, pins.size))
    val dock = visible.take(4)
    val pages = visible.drop(4).chunked(8).ifEmpty { listOf(emptyList()) }
    val pager = rememberPagerState(pageCount = { pages.size })
    val homeScroll = rememberScrollState()
    LaunchedEffect(style.spec.id) { homeScroll.scrollTo(0) }
    val suggestions by model.suggestions.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        WallpaperArt(style, Modifier.pointerInput(state.preferences.doubleTapAction) { detectTapGestures(onDoubleTap = { when (state.preferences.doubleTapAction) { "controls" -> controls(); "search" -> search() } }) })
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("morph / ${style.spec.name.lowercase()}", color = style.text.copy(alpha = .65f), fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = controls, modifier = Modifier.testTag("controls")) { Icon(Icons.Outlined.Menu, "Control center", tint = style.text, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = studio, modifier = Modifier.testTag("studio")) { Icon(Icons.Outlined.Star, "AI design studio", tint = style.text, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = themes, modifier = Modifier.testTag("themes")) { Icon(Icons.Outlined.Create, "Choose theme", tint = style.text, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = settings, modifier = Modifier.testTag("settings")) { Icon(Icons.Outlined.Settings, "Launcher settings", tint = style.text, modifier = Modifier.size(20.dp)) }
            }
            Column(Modifier.weight(1f).verticalScroll(homeScroll)) {
                if (!isDefault) TextButton(onClick = requestHome) { Text("Set as your home launcher", color = style.accent) }
                HomeClock(now, style)
                if (suggestions.isNotEmpty()) {
                    Text("For this time of day", color = style.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
                    Row { suggestions.forEach { app -> Box(Modifier.weight(1f)) { AppTile(app, model, style, false) } } }
                }
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (style.spec.homeStyle == "SHELF") "Your Shelf" else "Your essentials", color = style.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = widgets, modifier = Modifier.testTag("widgets")) { Text("Edit widgets", color = style.text.copy(alpha = .65f), fontSize = 11.sp) }
                }
                state.preferences.widgets.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { w -> WidgetCard(w, style, { model.widgetAction(w.id) }, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (state.appError) TextButton(onClick = model::refresh) { Text("Retry app discovery") }
                if (!state.ready) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("FAVORITES", color = style.muted, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = drawer, modifier = Modifier.testTag("all_apps")) { Text("All apps", color = style.text, fontSize = 12.sp) }
                }
                HorizontalPager(pager, Modifier.fillMaxWidth().height(if (pages.any { it.size > 4 }) 180.dp else 92.dp), verticalAlignment = Alignment.Top) { page ->
                    LazyVerticalGrid(columns = GridCells.Fixed(state.preferences.columnsOverride ?: style.spec.columns), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pages[page], key = { it.id }) { app -> AppTile(app, model, style, app.id in state.preferences.pinned) }
                    }
                }
                if (pages.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { repeat(pages.size) { i -> Box(Modifier.padding(4.dp).size(5.dp).background(if (i == pager.currentPage) style.text else style.muted.copy(alpha = .4f), CircleShape)) } }
                Spacer(Modifier.height(12.dp))
            }
            Surface(shape = RoundedCornerShape(32.dp), color = style.surface.copy(alpha = style.spec.dockOpacity), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    dock.forEach { app -> Box(Modifier.weight(1f)) { AppTile(app, model, style, app.id in state.preferences.pinned) } }
                    if (dock.isEmpty()) TextButton(onClick = drawer) { Text("Open app drawer") }
                }
            }
            Surface(shape = CircleShape, color = style.surface.copy(alpha = .5f), onClick = search, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp).height(48.dp).pointerInput(Unit) {
                var travel = 0f
                detectVerticalDragGestures(onDragStart = { travel = 0f }, onVerticalDrag = { change, distance -> change.consume(); travel += distance }, onDragEnd = { if (travel < -40) drawer() else if (travel > 40) controls() })
            }.testTag("search_home")) {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = style.text, modifier = Modifier.size(18.dp))
                    Text("Search your space", color = style.text.copy(alpha = .65f), fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Text("↑", color = style.text)
                }
            }
        }
    }
}

@Composable
internal fun Header(title: String, subtitle: String, style: Style, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to home", tint = style.text) }
        Column { Text(title, color = style.text, fontSize = 27.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = style.muted, fontSize = 12.sp) }
    }
}

@Composable
private fun Drawer(state: LauncherState, model: LauncherViewModel, style: Style, query: String, changeQuery: (String) -> Unit, back: () -> Unit) {
    val filtered = remember(state.apps, query) { AppSearch.filter(state.apps, query) }
    val suggestions by model.suggestions.collectAsStateWithLifecycle()
    val mode = state.preferences.drawerOverride ?: style.spec.drawer
    val results = if (mode == DrawerMode.FREQUENT && suggestions.isNotEmpty()) AppSearch.filter(suggestions, query) else filtered
    val focus = remember { FocusRequester() }
    LaunchedEffect(mode) { if (mode == DrawerMode.SEARCH_FIRST) focus.requestFocus() }
    Column(Modifier.fillMaxSize().padding(horizontal = style.spec.spacing.dp)) {
        Header("Your apps", "${state.apps.size} apps · ${style.spec.name}", style, back)
        OutlinedTextField(query, changeQuery, modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("app_search"), singleLine = true,
            label = { Text("Search apps") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { changeQuery("") }) { Icon(Icons.Outlined.Close, "Clear search") } }, shape = style.shape)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${results.size} results", color = style.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            FilterChip(selected = mode == DrawerMode.GRID, onClick = { model.drawer(DrawerMode.GRID) }, label = { Text("Grid") }, modifier = Modifier.testTag("grid_mode"))
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = mode == DrawerMode.LIST, onClick = { model.drawer(DrawerMode.LIST) }, label = { Text("List") }, modifier = Modifier.testTag("list_mode"))
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = mode == DrawerMode.CATEGORIES, onClick = { model.drawer(DrawerMode.CATEGORIES) }, label = { Text("Categories") })
            listOf(DrawerMode.RADIAL, DrawerMode.FREQUENT, DrawerMode.ALPHABETICAL, DrawerMode.SEARCH_FIRST).forEach { value ->
                Spacer(Modifier.width(8.dp)); FilterChip(mode == value, { model.drawer(value) }, label = { Text(value.name.lowercase().replace('_', ' ')) }, modifier = Modifier.testTag("mode_${value.name}"))
            }
        }
        if (state.appError) TextButton(onClick = model::refresh) { Text("Retry app discovery") }
        if (results.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Search, null, tint = style.muted, modifier = Modifier.size(36.dp))
                Text(if (state.ready) "No matching apps" else "Loading apps…", color = style.muted, modifier = Modifier.padding(12.dp))
            }
        } else if (mode == DrawerMode.RADIAL) {
            RadialDrawer(results, state, model, style)
        } else if (mode == DrawerMode.ALPHABETICAL) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                results.groupBy { it.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }.toSortedMap().forEach { (letter, apps) ->
                    item { Text(letter, color = style.accent, modifier = Modifier.padding(8.dp)) }
                    items(apps, key = { it.id }) { app -> AppTile(app, model, style, app.id in state.preferences.pinned, list = true) }
                }
            }
        } else if (mode == DrawerMode.CATEGORIES) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                results.groupBy { it.category }.toSortedMap().forEach { (category, apps) ->
                    item { Text(category, color = style.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(apps.chunked(state.preferences.columnsOverride ?: style.spec.columns)) { row -> Row { row.forEach { app -> Box(Modifier.weight(1f)) { AppTile(app, model, style, app.id in state.preferences.pinned) } }; repeat((state.preferences.columnsOverride ?: style.spec.columns) - row.size) { Spacer(Modifier.weight(1f)) } } }
                }
            }
        } else if (mode == DrawerMode.GRID) {
            LazyVerticalGrid(GridCells.Fixed(state.preferences.columnsOverride ?: style.spec.columns), modifier = Modifier.fillMaxSize().testTag("app_grid"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(results, key = { it.id }) { app -> AppTile(app, model, style, app.id in state.preferences.pinned) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().testTag("app_list"), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(results, key = { it.id }) { app -> AppTile(app, model, style, app.id in state.preferences.pinned, list = true) }
            }
        }
    }
}

@Composable
internal fun AppTile(app: AppEntry, model: LauncherViewModel, style: Style, pinned: Boolean, list: Boolean = false) {
    var menu by remember(app.id) { mutableStateOf(false) }
    var shortcuts by remember(app.id) { mutableStateOf<List<android.content.pm.ShortcutInfo>>(emptyList()) }
    LaunchedEffect(menu, app.id) { if (menu) shortcuts = model.appsRepository.shortcuts(app) }
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, app.id) { value = model.appsRepository.icon(app) }
    val icon: @Composable () -> Unit = {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(style.spec.iconRadius.dp)).background(if (style.spec.monochromeIcons) style.text else style.surface.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap!!, null, modifier = Modifier.fillMaxSize().padding(if (style.spec.monochromeIcons) 10.dp else 4.dp),
                colorFilter = if (style.spec.monochromeIcons) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null)
            else Text(app.label.take(1).uppercase(), color = style.accent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
    Box {
        val touch = Modifier.combinedClickable(onClick = { model.launch(app) }, onLongClick = { menu = true }, onLongClickLabel = "App options")
            .semantics { role = Role.Button; contentDescription = "${app.label}${if (pinned) ", pinned" else ""}" }
        if (list) {
            Row(touch.fillMaxWidth().clip(style.shape).background(style.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                icon()
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(app.label, color = style.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, color = style.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (pinned) Icon(Icons.Outlined.Star, "Pinned", tint = style.accent, modifier = Modifier.size(16.dp))
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Options for ${app.label}", tint = style.muted) }
            }
        } else {
            Column(touch.fillMaxWidth().heightIn(min = 84.dp).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                icon()
                Text(app.label, color = style.text, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            shortcuts.forEach { shortcut -> DropdownMenuItem(text = { Text(shortcut.shortLabel?.toString() ?: "App shortcut") }, onClick = { menu = false; runCatching { model.appsRepository.launchShortcut(shortcut) } }) }
            DropdownMenuItem(text = { Text(if (pinned) "Unpin from home" else "Pin to home") }, onClick = { model.pin(app); menu = false })
            DropdownMenuItem(text = { Text("Hide from suggestions") }, onClick = { model.hideSuggestion(app); menu = false })
            DropdownMenuItem(text = { Text("App info") }, onClick = {
                menu = false
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))) }
            })
        }
    }
}

@Composable
private fun Themes(model: LauncherViewModel, style: Style, dark: Boolean, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Find your feel", "Offline themes · Original, OS-inspired designs", style, back)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.testTag("theme_gallery")) {
            items(model.themes.themes, key = { it.id }) { theme ->
                val preview = Style(theme, if (dark) theme.dark else theme.light)
                Surface(shape = RoundedCornerShape(24.dp), color = preview.surface, modifier = Modifier.fillMaxWidth().testTag("theme_${theme.id}"), onClick = { model.theme(theme.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(72.dp).height(100.dp).clip(RoundedCornerShape(14.dp)).background(preview.bg).padding(8.dp)) {
                            Text("09:41", fontSize = 18.sp, color = preview.text, fontFamily = preview.font)
                            Spacer(Modifier.height(12.dp))
                            repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { Box(Modifier.size(14.dp).clip(RoundedCornerShape((theme.iconRadius / 3).dp)).background(if (theme.monochromeIcons) preview.text else preview.accent.copy(alpha = .8f))) } }; Spacer(Modifier.height(4.dp)) }
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.fillMaxWidth().height(12.dp).background(preview.surface, RoundedCornerShape(6.dp)))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(theme.name, color = preview.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text(theme.description, color = preview.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                            Text(if (theme.id == style.spec.id) "APPLIED" else "TAP TO APPLY", color = preview.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 12.dp))
                        }
                        if (theme.id == style.spec.id) Icon(Icons.Outlined.CheckCircle, "Current theme", tint = preview.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun Preferences(state: LauncherState, model: LauncherViewModel, style: Style, isDefault: Boolean, requestHome: () -> Unit, homeSettings: () -> Unit, back: () -> Unit, automation: () -> Unit, glance: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Make it yours", "Launcher settings", style, back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsCard("Home launcher", style) {
                Text(if (isDefault) "MorphOS is your default home." else "Select MorphOS as the default home app.", color = style.muted)
                Button(onClick = if (isDefault) homeSettings else requestHome) { Text(if (isDefault) "Change home app" else "Set as default") }
                Text("You can return to your previous launcher in Android Settings → Apps → Default apps → Home app.", color = style.muted, fontSize = 12.sp)
            }
            SettingsCard("Appearance", style) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Appearance.entries.forEach { value -> FilterChip(selected = state.preferences.appearance == value, onClick = { model.appearance(value) }, label = { Text(value.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                OutlinedButton(onClick = model::wallpaperAccent) { Text("Use wallpaper accent") }
            }
            SettingsCard("App layout", style) {
                val columns = state.preferences.columnsOverride ?: style.spec.columns
                Text("$columns columns", color = style.muted)
                Slider(value = columns.toFloat(), onValueChange = { model.columns(it.toInt()) }, valueRange = 3f..6f, steps = 2)
                Text("The first four pinned apps appear in your dock. Long press an app to pin it. Home supports up to 24 pins.", color = style.muted, fontSize = 12.sp)
            }
            SettingsCard("Privacy & capabilities", style) {
                Text("The launcher works offline. Personal data stays on your phone. AI requests contain your prompt and theme; reference images are sent only when you tap Analyze.", color = style.muted, fontSize = 13.sp)
                Text("Themes style MorphOS screens. Android controls its notification shade and secure lock screen.", color = style.muted, fontSize = 13.sp)
            }
            SettingsCard("Local app suggestions", style) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Learn MorphOS launches", color = style.text, modifier = Modifier.weight(1f)); Switch(state.preferences.predictionsEnabled, model::predictions) }
                Text("With your opt-in, launcher selections stay in a bounded local history. Three repeated launches are needed before suggestions appear. Usage Access optionally adds other app launches.", color = style.muted, fontSize = 12.sp)
                val context = LocalContext.current
                OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }) { Text("Manage Usage Access") }
                TextButton(onClick = model::clearHistory) { Text("Delete local selection history & reset hidden apps") }
            }
            SettingsCard("Adapt & explore", style) {
                OutlinedButton(onClick = automation) { Text("Automation rules") }
                OutlinedButton(onClick = glance) { Text("Glance screen") }
                Text("Double tap an empty wallpaper area", color = style.muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("controls", "search", "none").forEach { action -> FilterChip(state.preferences.doubleTapAction == action, { model.gesture(action) }, label = { Text(action) }) } }
            }
            SettingsCard("Recovery", style) {
                Text("Restore the Oxygen theme, system appearance, and automatic app arrangement.", color = style.muted, fontSize = 13.sp)
                OutlinedButton(onClick = { confirmReset = true }) { Text("Reset launcher preferences") }
            }
            Text("MorphOS AI · 0.2.0 · Nebius design integration", color = style.muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Reset preferences?") }, text = { Text("This removes your pins and restores the default theme and layout.") }, confirmButton = { TextButton(onClick = { model.reset(); confirmReset = false }) { Text("Reset") } }, dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } })
}

@Composable
private fun SettingsCard(title: String, style: Style, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = style.shape, color = style.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = style.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            content()
        }
    }
}
