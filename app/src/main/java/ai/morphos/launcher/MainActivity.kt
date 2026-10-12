package ai.morphos.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import ai.morphos.launcher.ui.LauncherRoot
import ai.morphos.launcher.ui.LauncherViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: LauncherViewModel by viewModels()
    private val isDefault = MutableStateFlow(false)
    private val homeRequest = MutableStateFlow(0)
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { updateRole() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LauncherRoot(model, isDefault, homeRequest, ::requestHome, ::homeSettings) }
    }
    override fun onResume() { super.onResume(); updateRole(); model.refresh() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeRequest.value++
    }
    private fun updateRole() {
        val roles = getSystemService(RoleManager::class.java)
        isDefault.value = roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME)
    }
    private fun requestHome() {
        val roles = getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            runCatching { roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }.onFailure { homeSettings() }
        } else homeSettings()
    }
    private fun homeSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
}
