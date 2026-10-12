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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
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
    val oxygen = spec.homeStyle == "SHELF"
    val nothing = spec.homeStyle == "GEOMETRIC"
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
                statusBarStyle = SystemBarStyle.auto(0, 0) { dark || style.oxygen },
                navigationBarStyle = SystemBarStyle.auto(style.bg.toArgb(), style.bg.toArgb()) { dark || style.oxygen }
            )
        }
    }
    ClipboardBridge(model.clipboard)
    val sharedClip by model.clipboard.incoming.collectAsStateWithLifecycle()
    LaunchedEffect(sharedClip) { if (sharedClip != null) screen = "clipboard" }
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
                AnimatedContent(screen, modifier = Modifier.fillMaxSize(), transitionSpec = {
                    if (style.spec.reducedMotion) fadeIn(snap()) togetherWith fadeOut(snap())
                    else (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 12 }) togetherWith fadeOut(tween(100))
                }, label = "Launcher screen") { route ->
                when (route) {
                    "home" -> PhoneHome(state, model, style, isDefault, requestHome, { screen = "settings" }, { screen = "themes" }, { screen = "drawer"; query = "" }, { screen = "studio" }, { screen = "widgets" }, { screen = "search" }, { screen = "controls" })
                    "controls" -> ControlDashboard(style, { screen = "home" }, { screen = "notifications" })
                    "notifications" -> NotificationDashboard(style, { screen = "controls" })
                    "automation" -> AutomationStudio(state, model, style, { screen = "home" })
                    "glance" -> GlanceScreen(style, { screen = "home" })
                    "search" -> SearchDashboard(state, model, style, { screen = "home" }, { screen = it })
                    "widgets" -> WidgetStudio(state, model, style, { screen = "home" })
                    "studio" -> DesignStudio(state, model, style, dark, { screen = "home" })
                    "drawer" -> PhoneDrawer(state, model, style, query, { query = it }, { screen = "home"; query = "" })
                    "community" -> CommunityScreen(state, model, style, { screen = "themes" })
                    "clipboard" -> ClipboardScreen(model.clipboard, style, { screen = "settings" })
                    "themes" -> Themes(model, style, dark, { screen = "home" }, { screen = "community" })
                    "settings" -> Preferences(state, model, style, isDefault, requestHome, homeSettings, { screen = "home" }, { screen = "automation" }, { screen = "glance" }, { screen = "clipboard" }, { screen = "community" })
                }
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
internal fun AppTile(app: AppEntry, model: LauncherViewModel, style: Style, pinned: Boolean, list: Boolean = false, dock: Boolean = false, compact: Boolean = false, interactive: Boolean = true) {
    var menu by remember(app.id) { mutableStateOf(false) }
    var shortcuts by remember(app.id) { mutableStateOf<List<android.content.pm.ShortcutInfo>>(emptyList()) }
    LaunchedEffect(menu, app.id) { if (menu) shortcuts = model.appsRepository.shortcuts(app) }
    val context = LocalContext.current
    val settings by model.state.collectAsStateWithLifecycle()
    val onHome = settings.preferences.homeItems?.any { it.kind == HomeKind.APP && it.target == app.id } ?: pinned
    val monochrome = settings.preferences.iconTreatment == "monochrome" || settings.preferences.iconTreatment == "theme" && style.spec.monochromeIcons
    val monoLayer by produceState<ImageBitmap?>(null, app.id, monochrome) { value = if (monochrome) model.appsRepository.monochromeIcon(app) else null }
    val bitmap by produceState<ImageBitmap?>(null, app.id) { value = model.appsRepository.icon(app) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .90f else 1f, if (style.spec.reducedMotion) snap() else spring(dampingRatio = .65f, stiffness = 550f), label = "Icon press")
    val themed = settings.preferences.iconTreatment == "theme"
    val glyph = if (themed) systemGlyph(app.packageName) else null
    val icon: @Composable () -> Unit = {
        val extent = if (dock) 52.dp else if (compact) 42.dp else 50.dp
        if (glyph != null) ThemedGlyph(glyph, style, monochrome, Modifier.size(extent).scale(scale))
        else
        Box(Modifier.size(extent).scale(scale).then(if (monochrome) Modifier.background(style.surface, CircleShape) else if (themed) Modifier.background(style.surface.copy(alpha = .94f), RoundedCornerShape(32)) else Modifier), contentAlignment = Alignment.Center) {
            if (monoLayer != null) Image(monoLayer!!, null, modifier = Modifier.size(30.dp), colorFilter = ColorFilter.tint(style.text))
            else if (bitmap != null) Image(bitmap!!, null, modifier = Modifier.fillMaxSize().padding(if (monochrome || themed) 7.dp else 0.dp),
                colorFilter = if (monochrome) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null)
            else Text(app.label.take(1).uppercase(), color = style.text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
    Box {
        val touch = Modifier.combinedClickable(interactionSource = interaction, indication = null, onClick = { if (interactive) model.launch(app) }, onLongClick = { if (interactive) menu = true }, onLongClickLabel = "App options")
            .semantics { role = Role.Button; contentDescription = "${app.label}${if (onHome) ", on home" else ""}" }
        if (list) {
            Row(touch.fillMaxWidth().clip(style.shape).background(style.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                icon()
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(app.label, color = style.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, color = style.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onHome) Icon(Icons.Outlined.Star, "Pinned", tint = style.accent, modifier = Modifier.size(16.dp))
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Options for ${app.label}", tint = style.muted) }
            }
        } else {
            Column(touch.fillMaxWidth().heightIn(min = if (dock || compact) 60.dp else 78.dp).padding(if (compact) 2.dp else 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                icon()
                if (!dock && (interactive || !compact)) Text(app.label, color = style.text, fontSize = if (compact) 10.sp else 11.sp, lineHeight = if (compact) 12.sp else 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (compact) 3.dp else 6.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            shortcuts.forEach { shortcut -> DropdownMenuItem(text = { Text(shortcut.shortLabel?.toString() ?: "App shortcut") }, onClick = { menu = false; runCatching { model.appsRepository.launchShortcut(shortcut) } }) }
            DropdownMenuItem(text = { Text(if (onHome) "Remove from home" else "Add to home") }, onClick = { model.pin(app); menu = false })
            DropdownMenuItem(text = { Text(if (app.id in settings.preferences.dockApps) "Remove from dock" else "Add to dock") }, onClick = { model.dock(app); menu = false })
            DropdownMenuItem(text = { Text("Hide from suggestions") }, onClick = { model.hideSuggestion(app); menu = false })
            DropdownMenuItem(text = { Text("App info") }, onClick = {
                menu = false
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))) }
            })
        }
    }
}

@Composable
private fun Themes(model: LauncherViewModel, style: Style, dark: Boolean, back: () -> Unit, community: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Find your feel", "Offline themes · Original, OS-inspired designs", style, back)
        OutlinedButton(onClick = community, modifier = Modifier.fillMaxWidth()) { Text("Community · explore & share themes") }
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
private fun Preferences(state: LauncherState, model: LauncherViewModel, style: Style, isDefault: Boolean, requestHome: () -> Unit, homeSettings: () -> Unit, back: () -> Unit, automation: () -> Unit, glance: () -> Unit, clipboard: () -> Unit, community: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Make it yours", "Launcher settings", style, back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsCard("Home launcher", style) {
                Text(if (isDefault) "MorphOS is your default home." else "Select MorphOS as the default home app.", color = style.muted)
                Button(onClick = if (isDefault) homeSettings else requestHome) { Text(if (isDefault) "Change home app" else "Set as default") }
                Text("You can return to your previous launcher in Android Settings → Apps → Default apps → Home app.", color = style.muted, fontSize = 12.sp)
            }
            SettingsCard("Connected spaces", style) {
                OutlinedButton(onClick = clipboard) { Text("Windows shared clipboard") }
                OutlinedButton(onClick = community) { Text("Theme community") }
            }
            SettingsCard("Appearance", style) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Appearance.entries.forEach { value -> FilterChip(selected = state.preferences.appearance == value, onClick = { model.appearance(value) }, label = { Text(value.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                OutlinedButton(onClick = model::wallpaperAccent) { Text("Use wallpaper accent") }
            }
            SettingsCard("Motion", style) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Reduce motion", color = style.text, modifier = Modifier.weight(1f)); Switch(style.spec.reducedMotion, { value -> model.reduceMotion(value) }) }
            }
            SettingsCard("App icons", style) {
                Text("Original keeps installed icons. Theme gives system apps coordinated geometric icons and frames other apps consistently. Nothing uses monochrome circles; Oxygen uses colored rounded tiles. Monochrome uses app-provided layers with grayscale fallback.", color = style.muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("original", "monochrome", "theme").forEach { value -> FilterChip(state.preferences.iconTreatment == value, { model.icons(value) }, label = { Text(value.replaceFirstChar { it.uppercase() }) }) } }
            }
            SettingsCard("App layout", style) {
                val columns = state.preferences.columnsOverride ?: style.spec.columns
                Text("$columns columns", color = style.muted)
                Slider(value = columns.toFloat(), onValueChange = { model.columns(it.toInt()) }, valueRange = 3f..6f, steps = 2)
                Text("Long press an app to add it to home or your four-app dock. Use Edit home to move apps and widgets across pages.", color = style.muted, fontSize = 12.sp)
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
            Text("MorphOS AI · 0.3.0 · Nebius design integration", color = style.muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
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
