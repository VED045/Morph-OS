package ai.morphos.launcher.ui

import ai.morphos.launcher.domain.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable internal fun PhoneDrawer(state: LauncherState, model: LauncherViewModel, style: Style, query: String, changeQuery: (String) -> Unit, back: () -> Unit) {
    val mode = state.preferences.drawerOverride ?: style.spec.drawer
    val suggested by model.suggestions.collectAsStateWithLifecycle()
    val results = remember(state.apps, query, mode, suggested) { AppSearch.filter(if (mode == DrawerMode.FREQUENT && suggested.isNotEmpty()) suggested else state.apps, query) }
    var menu by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val oxygen = style.oxygen
    val panel = if (oxygen) style.bg.copy(alpha = .8f) else style.surface
    val ink = if (oxygen) style.text else if (style.palette.surface == "#F1F1EE") androidx.compose.ui.graphics.Color(0xFF151515) else style.text
    val drawerStyle = style.copy(palette = style.palette.copy(background = if (oxygen) style.palette.background else style.palette.surface, foreground = if (oxygen) style.palette.foreground else if (style.palette.surface == "#F1F1EE") "#151515" else style.palette.foreground))
    Box(Modifier.fillMaxSize()) {
        WallpaperArt(style)
        Surface(color = panel, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Box(Modifier.padding(top = 8.dp).width(34.dp).height(3.dp).align(Alignment.CenterHorizontally).background(ink.copy(alpha = .3f), CircleShape))
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to home", tint = ink) }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                        listOf("All" to DrawerMode.GRID, "Categories" to DrawerMode.CATEGORIES).forEach { (title, value) ->
                            Surface(onClick = { model.drawer(value) }, color = if (mode == value) ink.copy(alpha = .12f) else androidx.compose.ui.graphics.Color.Transparent, shape = CircleShape, modifier = Modifier.testTag(if (value == DrawerMode.GRID) "grid_mode" else "category_mode")) {
                                Text(title, color = ink, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp))
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("drawer_options")) { Icon(Icons.Outlined.MoreVert, "Drawer options", tint = ink) }
                        DropdownMenu(menu, { menu = false }) { DrawerMode.entries.forEach { value ->
                            DropdownMenuItem(text = { Text(value.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }, onClick = { model.drawer(value); menu = false }, modifier = Modifier.testTag(if (value == DrawerMode.LIST) "list_mode" else "mode_${value.name}"))
                        } }
                    }
                }
                if (!oxygen) DrawerSearch(query, changeQuery, ink, drawerStyle)
                if (state.appError) TextButton(onClick = model::refresh) { Text("Retry app discovery") }
                Box(Modifier.weight(1f)) {
                    if (results.isEmpty()) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Search, null, tint = ink.copy(alpha = .45f)); Text(if (state.ready) "No matching apps" else "Loading apps…", color = ink.copy(alpha = .6f), modifier = Modifier.padding(12.dp)) }
                    else when (mode) {
                        DrawerMode.RADIAL -> RadialDrawer(results, state, model, drawerStyle)
                        DrawerMode.LIST, DrawerMode.FREQUENT, DrawerMode.SEARCH_FIRST -> LazyColumn(Modifier.fillMaxSize().testTag("app_list"), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(results, key = { it.id }) { AppTile(it, model, drawerStyle, it.id in state.preferences.pinned, list = true) } }
                        DrawerMode.CATEGORIES -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                            results.groupBy { it.category }.toSortedMap().forEach { (category, apps) ->
                                item { Column(Modifier.background(ink.copy(alpha = .055f), RoundedCornerShape(24.dp)).padding(12.dp)) {
                                    Text(category, color = ink.copy(alpha = .7f), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                                    apps.chunked(4).forEach { row -> Row { row.forEach { app -> Box(Modifier.weight(1f)) { AppTile(app, model, drawerStyle, app.id in state.preferences.pinned) } }; repeat(4 - row.size) { Spacer(Modifier.weight(1f)) } } }
                                } }
                            }
                        }
                        else -> Row(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(GridCells.Fixed(state.preferences.columnsOverride ?: style.spec.columns), state = grid, modifier = Modifier.weight(1f).fillMaxHeight().testTag("app_grid"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp)) {
                                items(results, key = { it.id }) { AppTile(it, model, drawerStyle, it.id in state.preferences.pinned) }
                            }
                            Column(Modifier.width(22.dp).fillMaxHeight().padding(top = 10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                results.map { it.label.firstOrNull()?.uppercaseChar() ?: '#' }.distinct().sorted().forEach { letter ->
                                    Text(letter.toString(), color = ink.copy(alpha = .65f), fontSize = 9.sp, modifier = Modifier.width(22.dp).heightIn(min = 22.dp).clickable { val at = results.indexOfFirst { it.label.firstOrNull()?.uppercaseChar() == letter }; scope.launch { if (style.spec.reducedMotion) grid.scrollToItem(at) else grid.animateScrollToItem(at) } }.semantics { contentDescription = "Jump to $letter"; role = Role.Button })
                                }
                            }
                        }
                    }
                }
                if (oxygen) DrawerSearch(query, changeQuery, ink, drawerStyle)
                Text("${results.size} apps · Hold an icon to add it to home", color = ink.copy(alpha = .45f), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
            }
        }
    }
}
@Composable private fun DrawerSearch(query: String, change: (String) -> Unit, ink: androidx.compose.ui.graphics.Color, style: Style) {
    OutlinedTextField(query, { change(it.take(200)) }, placeholder = { Text("Search apps", color = ink.copy(alpha = .5f)) }, leadingIcon = { Icon(Icons.Outlined.Search, null, tint = ink.copy(alpha = .65f)) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { change("") }) { Icon(Icons.Outlined.Close, "Clear search", tint = ink) } },
        singleLine = true, shape = CircleShape, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = ink, focusedBorderColor = ink.copy(alpha = .3f), unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = ink.copy(alpha = .08f), unfocusedContainerColor = ink.copy(alpha = .08f)), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("app_search"))
}
