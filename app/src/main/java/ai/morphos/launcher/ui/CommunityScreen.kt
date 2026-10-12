package ai.morphos.launcher.ui

import ai.morphos.launcher.data.*
import ai.morphos.launcher.domain.*
import android.content.ClipData
import android.content.ClipboardManager
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
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.json.JSONObject

@Composable internal fun CommunityScreen(state: LauncherState, model: LauncherViewModel, style: Style, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("My home") }
    var invite by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Connect to a hosted community, or share a theme file directly.") }
    var busy by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(emptyList<CommunityTheme>()) }
    var next by remember { mutableStateOf<Long?>(null) }
    var preview by remember { mutableStateOf<ThemeSpec?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    fun action(block: suspend () -> Unit) { scope.launch { busy = true; try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { status = e.message?.take(180) ?: "Could not complete community request" } finally { busy = false } } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) action {
        val raw = Json.encodeToString(model.currentTheme())
        withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(raw) } }
        status = "Theme file exported. It contains design settings only."
    } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) action {
        preview = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { val bytes = it.boundedBytes(8192); require(bytes.size <= 8192); ThemeValidator.decode(bytes.toString(Charsets.UTF_8)) } }
        revision = state.preferences.revision; status = "Review this theme before applying."
    } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Header("Community", "Share a home. Make it your own.", style, back)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ export.launch("morphos-theme.json") }) { Text("Export theme") }
                OutlinedButton({ import.launch(arrayOf("application/json", "text/plain")) }) { Text("Import file") }
            }
            OutlinedTextField(host, { host = it.take(300) }, label = { Text("Community HTTPS URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button({ action { val result = CommunityClient.browse(host); entries = result.first; next = result.second; status = "${entries.size} reviewed themes loaded." } }, enabled = !busy && host.isNotBlank()) { Text("Explore community") }
            entries.forEach { entry ->
                Surface(onClick = { preview = entry.theme; revision = state.preferences.revision }, shape = RoundedCornerShape(24.dp), color = style.surface) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.theme.name, color = style.text, fontSize = 20.sp)
                        Text("By ${entry.author}", color = style.muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(entry.theme.light.background, entry.theme.light.surface, entry.theme.light.accent).forEach { Box(Modifier.size(28.dp).background(it.color(), RoundedCornerShape(10.dp))) } }
                        Text(entry.theme.description, color = style.muted, fontSize = 12.sp)
                    }
                }
            }
            if (next != null) TextButton({ action { val result = CommunityClient.browse(host, next); entries = result.first; next = result.second } }, enabled = !busy) { Text("Next page") }
            preview?.let { theme ->
                Surface(color = theme.light.background.color(), shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(theme.name, color = theme.light.foreground.color(), fontSize = 24.sp)
                        Text("09:41", color = theme.light.foreground.color(), fontSize = 48.sp)
                        Text("${theme.columns} columns · ${theme.drawer.name.lowercase()} · ${theme.wallpaperStyle.lowercase()}", color = theme.light.muted.color())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(4) { Box(Modifier.size(40.dp).background(theme.light.accent.color(), RoundedCornerShape(theme.iconRadius.dp))) } }
                    }
                }
                Button({ model.importTheme(theme, revision); preview = null }, enabled = revision == state.preferences.revision) { Text("Apply preview · undo available") }
            }
            HorizontalDivider()
            Text("Publish your design", color = style.text, fontSize = 22.sp)
            Text("Uploads contain the theme palette and layout settings, never your apps, contacts, photos or widgets' personal data. An administrator reviews uploads before they appear publicly.", color = style.muted, fontSize = 12.sp)
            OutlinedTextField(title, { title = it.take(60) }, label = { Text("Theme title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it.take(200) }, label = { Text("Your creator key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button({ action { val theme = model.currentTheme().copy(id = "community-" + java.util.UUID.randomUUID().toString().take(12), name = title); val response = CommunityClient.publish(host, key, theme); status = "Uploaded for review. Submission: ${response.getString("id")}" } }, enabled = !busy && key.isNotBlank() && title.isNotBlank()) { Text("Upload current theme") }
            Text("New creator", color = style.text)
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Creator display name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(invite, { invite = it.take(200) }, label = { Text("Community invite") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedButton({ action { key = CommunityClient.request(host, "creators", invite, JSONObject().put("name", name)).getString("key"); invite = ""; status = "Creator key created. Copy it to your password manager before leaving this screen." } }, enabled = !busy && name.length >= 2 && invite.isNotBlank()) { Text("Create creator key") }
            if (key.isNotBlank()) TextButton({ context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("MorphOS creator key", key).apply { description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) } }); status = "Key copied. Keep it private." }) { Text("Copy creator key") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, color = style.muted, fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))
        }
    }
}
