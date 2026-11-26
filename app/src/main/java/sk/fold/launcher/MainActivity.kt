package sk.fold.launcher

import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import sk.fold.launcher.data.HomeItem
import sk.fold.launcher.data.ItemType
import sk.fold.launcher.data.Store
import sk.fold.launcher.data.Widgets
import sk.fold.launcher.ui.FoldTheme
import sk.fold.launcher.ui.LauncherRoot
import sk.fold.launcher.ui.LauncherUi

class MainActivity : ComponentActivity() {
    var ui: LauncherUi? = null
    private var pendingWidget: Pair<Int, AppWidgetProviderInfo>? = null

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val p = pendingWidget ?: return@registerForActivityResult
        if (r.resultCode == RESULT_OK) configureOrPlace(p.first, p.second)
        else { Widgets.delete(p.first); pendingWidget = null }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson().toByteArray()) }
            toast("Záloha uložená")
        }.onFailure { toast("Zálohu sa nepodarilo uložiť") }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            val old = text?.let { Store.importJson(it) }
            if (old == null) toast("Súbor nie je platná záloha")
            else { old.forEach { Widgets.delete(it) }; toast("Obnovené (widgety treba pridať znova)") }
        }
    }

    private val locationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) lifecycleScope.launch { sk.fold.launcher.data.Weather.refresh(this@MainActivity, force = true) }
        else toast("Bez polohy zadaj mesto v nastaveniach hodín")
        ui?.let { it.resumeTick++ }
    }

    private val calendarLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        ui?.let { it.resumeTick++ }
    }

    fun requestCalendar() = calendarLauncher.launch(android.Manifest.permission.READ_CALENDAR)

    fun requestLocation() = locationLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ui?.resumeTick = (ui?.resumeTick ?: 0) + 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        sk.fold.launcher.data.Badges.rebind(this)
        setContent {
            val settings by Store.settings.collectAsState()
            val state = remember { LauncherUi(this) }
            ui = state
            FoldTheme(settings) { LauncherRoot(state) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) ui?.home()
    }

    override fun onStart() {
        super.onStart()
        runCatching { Widgets.host.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { Widgets.host.stopListening() }
    }

    override fun onResume() {
        super.onResume()
        ui?.let { it.resumeTick++ }
        sk.fold.launcher.data.Badges.rebind(this)
    }

    fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    fun isDefaultLauncher(): Boolean =
        runCatching { getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME) }.getOrDefault(false)

    fun requestDefaultLauncher() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            runCatching { roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                .onFailure { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        } else runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }

    fun exportBackup() = exportLauncher.launch("fold-launcher-zaloha.json")
    fun importBackup() = importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))

    // ---------- pridávanie widgetu ----------

    fun startAddWidget(info: AppWidgetProviderInfo) {
        val id = Widgets.host.allocateAppWidgetId()
        pendingWidget = id to info
        val ok = runCatching { Widgets.manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)
        if (ok) configureOrPlace(id, info)
        else bindLauncher.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        )
    }

    private fun configureOrPlace(id: Int, info: AppWidgetProviderInfo) {
        val optional = (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        if (info.configure != null && !optional) {
            try {
                Widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
                return
            } catch (e: Exception) {
                // niektoré widgety konfiguráciu nespustia – pridáme ho rovno
            }
        }
        place(id, info)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIG) {
            val p = pendingWidget ?: return
            if (resultCode == RESULT_OK) place(p.first, p.second)
            else { Widgets.delete(p.first); pendingWidget = null }
        }
    }

    private fun place(id: Int, info: AppWidgetProviderInfo) {
        pendingWidget = null
        val screen = ui?.screen ?: Store.lastScreen
        val (w, h) = Widgets.span(screen, info, resources.displayMetrics.density)
        val page = Store.add(screen, HomeItem(Store.newId(), ItemType.WIDGET, Store.lastPage, 0, 0, w, h, widgetId = id))
        ui?.showPage(page)
    }

    companion object { const val REQ_CONFIG = 4711 }
}
