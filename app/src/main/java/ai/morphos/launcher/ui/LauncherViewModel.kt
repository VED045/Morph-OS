package ai.morphos.launcher.ui

import android.content.pm.LauncherApps
import android.os.UserHandle
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.morphos.launcher.data.*
import ai.morphos.launcher.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LauncherState(
    val preferences: LauncherPreferences = LauncherPreferences(),
    val apps: List<AppEntry> = emptyList(),
    val ready: Boolean = false,
    val appError: Boolean = false
)

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    val themes: ThemeRepository,
    val appsRepository: AppsRepository,
    private val designer: DesignerClient,
    private val personal: PersonalRepository,
    val phoneInference: PhoneInference,
    val clipboard: ClipboardSync,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val apps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val ready = MutableStateFlow(false)
    private val appError = MutableStateFlow(false)
    val state = combine(prefs.preferences, apps, ready, appError) { p, a, r, e -> LauncherState(p, a, r, e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherState())
    val history = personal.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val contacts = MutableStateFlow<List<ContactEntry>>(emptyList())
    val suggestions = combine(state, history) { s, h ->
        if (!s.preferences.predictionsEnabled) emptyList() else AppPrediction.rank(h, System.currentTimeMillis(), s.preferences.hiddenApps.toSet()).mapNotNull { key -> s.apps.firstOrNull { it.packageName == key } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun refreshContacts() { viewModelScope.launch { contacts.value = personal.contacts() } }
    fun usageAllowed() = personal.usageAllowed()
    fun predictions(enabled: Boolean) = save { prefs.predictions(enabled); if (enabled && personal.usageAllowed()) personal.importUsage() }
    fun hideSuggestion(app: AppEntry) = save { prefs.hideApp(app.packageName) }
    fun clearHistory() = save { personal.clear(); prefs.clearHidden() }
    fun pinContact(contact: ContactEntry) = save { prefs.pinContact(contact.key) }
    fun recordContact(contact: ContactEntry) = save { personal.record(contact.key, "contact") }
    private val events = Channel<String>(Channel.BUFFERED)
    init { viewModelScope.launch { prefs.preferences.map { p -> p.rules.any { it.enabled } }.distinctUntilChanged().collect { enabled -> AutomationWorker.schedule(context, enabled) } } }
    val messages = events.receiveAsFlow()
    val proposal = MutableStateFlow<DesignProposal?>(null)
    val designing = MutableStateFlow(false)
    val widgetProposal = MutableStateFlow<WidgetProposal?>(null)
    fun proposeWidget(prompt: String, endpoint: String, token: String) {
        if (designing.value) return
        viewModelScope.launch {
            designing.value = true; designError.value = null; widgetProposal.value = null
            val request = WidgetRequest(prompt, state.value.preferences.revision)
            try { widgetProposal.value = inference(state.value.preferences.localAiMode, { designer.widget(endpoint, token, request) }, { phoneInference.widget(request) }) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { designError.value = "Widget inference unavailable. Your existing widgets are unchanged." }
            finally { designing.value = false }
        }
    }
    val designError = MutableStateFlow<String?>(null)
    fun currentTheme(): ThemeSpec = state.value.preferences.customTheme ?: themes.find(state.value.preferences.themeId)
    fun proposeReference(prompt: String, image: String, endpoint: String, token: String) {
        if (designing.value) return
        viewModelScope.launch {
            designing.value = true; designError.value = null; proposal.value = null
            try {
                val p = state.value.preferences; val current = currentTheme().copy(columns = p.columnsOverride ?: currentTheme().columns, drawer = p.drawerOverride ?: currentTheme().drawer)
                val request = ImageDesignRequest(prompt.ifBlank { "Adapt this image into my launcher" }, current, p.revision, image)
                proposal.value = inference(p.localAiMode, { designer.reference(endpoint, token, request) }, {
                    if (phoneInference.visionEnabled()) phoneInference.reference(request)
                    else {
                        val sampled = ImagePersonalizer.palette(image, p.revision)
                        val base = DesignEngine.apply(current, sampled.patch)
                        val refined = phoneInference.design(DesignRequest("Refine these image-derived colors and this request: ${request.prompt}", base, p.revision))
                        refined.copy(patch = kotlinx.serialization.json.JsonObject(sampled.patch + refined.patch), explanation = "Image colors sampled locally; your text model refined the design. " + refined.explanation.take(350))
                    }
                })
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { designError.value = "No validated image proposal. Check the selected model, or use Extract colors for a model-free local preview." }
            finally { designing.value = false }
        }
    }
    fun propose(prompt: String, endpoint: String, token: String, complex: Boolean) {
        if (designing.value) return
        viewModelScope.launch {
            designing.value = true; designError.value = null; proposal.value = null
            val current = currentTheme()
            val p = state.value.preferences
            val effective = current.copy(columns = p.columnsOverride ?: current.columns, drawer = p.drawerOverride ?: current.drawer)
            try {
                require(prompt.isNotBlank() && prompt.length <= 2000)
                val request = DesignRequest(prompt, effective, p.revision, if (complex) "complex" else "fast")
                proposal.value = DesignEngine.local(prompt, p.revision) ?: inference(p.localAiMode, { designer.design(endpoint, token, request) }, { phoneInference.design(request) })
                DesignEngine.apply(effective, proposal.value!!.patch)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { designError.value = "No validated proposal. Check your selected model or connection, or try an offline command. Your layout is unchanged." }
            finally { designing.value = false }
        }
    }
    fun importTheme(theme: ThemeSpec, revision: Int) = save { prefs.apply(ThemeValidator.validate(theme), revision, "theme"); events.send("Community theme applied. Undo is available in Design studio.") }
    fun paletteReference(image: String) { viewModelScope.launch { try { proposal.value = ImagePersonalizer.palette(image, state.value.preferences.revision) } catch (_: Exception) { designError.value = "Could not sample this image" } } }
    fun applyProposal() = save {
        val result = proposal.value ?: return@save
        val p = state.value.preferences
        val current = currentTheme().copy(columns = p.columnsOverride ?: currentTheme().columns, drawer = p.drawerOverride ?: currentTheme().drawer)
        prefs.apply(DesignEngine.apply(current, result.patch), result.revision, if ("monochromeIcons" in result.patch) "theme" else null)
        proposal.value = null
        events.send("Design applied. Undo is available.")
    }
    fun undo() = save { prefs.travel(true); proposal.value = null }
    fun redo() = save { prefs.travel(false); proposal.value = null }
    fun addRule(rule: AutomationRule) = save { prefs.addRule(rule) }
    fun updateRule(rule: AutomationRule) = save { prefs.updateRule(rule) }
    fun removeRule(id: String) = save { prefs.removeRule(id) }
    fun gesture(action: String) = save { prefs.gesture(action) }
    fun wallpaperAccent() = save {
        val color = context.getSystemService(android.app.WallpaperManager::class.java).getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb() ?: error("Wallpaper colors unavailable")
        val accent = "#%06X".format(color and 0xFFFFFF)
        val theme = currentTheme()
        prefs.apply(theme.copy(schemaVersion = 2, light = theme.light.copy(accent = accent), dark = theme.dark.copy(accent = accent)), state.value.preferences.revision)
    }
    fun evaluateRules() = save { AutomationWorker.evaluate(context) }
    fun addWidget(widget: WidgetSpec, revision: Int) = save { prefs.addWidget(widget, revision); events.send("Widget added to home.") }
    fun removeWidget(id: String) = save { prefs.removeWidget(id) }
    fun updateWidget(widget: WidgetSpec) = save { prefs.updateWidget(widget) }
    fun moveWidget(id: String, direction: Int) = save { prefs.moveWidget(id, direction) }
    fun widgetAction(id: String) = save { prefs.widgetAction(id) }
    fun ensureWorkspace() = save { if (state.value.ready && state.value.apps.isNotEmpty()) prefs.ensureWorkspace(state.value.apps.map { it.id }) }
    fun homePage(add: Boolean, page: Int) = save { prefs.page(add, page) }
    fun moveHome(key: String, page: Int, column: Int, row: Int) = save { prefs.moveHome(key, page, column, row) }
    fun resizeHome(key: String, width: Int, height: Int) = save { prefs.resizeHome(key, width, height) }
    fun restoreWidget(id: String) = save { prefs.restoreWidget(id) }
    fun removeHome(key: String) = save { prefs.removeHome(key) }
    fun dock(app: AppEntry) = save { prefs.dock(app.id) }
    fun icons(value: String) = save { prefs.icons(value) }
    fun reduceMotion(value: Boolean) = save { val p = state.value.preferences; prefs.apply(currentTheme().copy(schemaVersion = 2, reducedMotion = value, columns = p.columnsOverride ?: currentTheme().columns, drawer = p.drawerOverride ?: currentTheme().drawer), p.revision) }
    fun localAiMode(value: String) = save { prefs.localAi(value) }
    private suspend fun <T> inference(mode: String, remote: suspend () -> T, local: suspend () -> T): T {
        if (mode == "local") return local()
        try { return remote() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { if (mode == "fallback" && phoneInference.installed()) { events.send("Nebius unavailable. Trying your on-device model."); return local() }; throw e }
    }
    private var refreshJob: Job? = null
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
        private fun changed() { appsRepository.invalidateIcons(); refresh() }
    }
    init { viewModelScope.launch { themes.load() }; appsRepository.watch(callback); refresh() }
    fun refresh() {
        refreshContacts()
        if (state.value.preferences.predictionsEnabled) viewModelScope.launch { runCatching { personal.importUsage() } }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try { apps.value = appsRepository.discover(); appError.value = false }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { appError.value = true; events.send("App discovery failed. Tap Retry to try again.") }
            finally { ready.value = true }
        }
    }
    private fun save(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { events.send("Could not save this change. Please try again.") }
    } }
    fun theme(id: String) = save { prefs.theme(themes.find(id).id) }
    fun appearance(value: Appearance) = save { prefs.appearance(value) }
    fun drawer(value: DrawerMode) = save { prefs.drawer(value) }
    fun columns(value: Int) = save { prefs.columns(value) }
    fun pin(app: AppEntry) = save {
        if (state.value.preferences.homeItems?.any { it.kind == HomeKind.APP && it.target == app.id } != true && state.value.preferences.pinned.size >= 24) events.send("Home supports 24 pinned apps. Unpin one to make room.")
        else prefs.togglePin(app.id)
    }
    fun reset() = save { prefs.reset() }
    fun launch(app: AppEntry) {
        try { appsRepository.launch(app); if (state.value.preferences.predictionsEnabled) save { personal.record(app.packageName, "app") } }
        catch (_: Exception) { viewModelScope.launch { events.send("This app is unavailable or blocked by your device. Refresh the app drawer.") }; refresh() }
    }
    override fun onCleared() { appsRepository.unwatch(callback); super.onCleared() }
}
