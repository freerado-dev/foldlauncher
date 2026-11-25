package sk.fold.launcher.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import sk.fold.launcher.App
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import sk.fold.launcher.data.Screen
import sk.fold.launcher.data.Store

@Composable
fun LauncherRoot(ui: LauncherUi) {
    val s by Store.settings.collectAsState()
    val view = LocalView.current
    val dark = LocalDark.current
    // Musí byť zaregistrovaný ako prvý, aby mali prednosť vnorené BackHandlery (nastavenia).
    BackHandler { ui.back() }
    val scope = rememberCoroutineScope()
    SideEffect { ui.scope = scope }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = if (maxWidth >= 600.dp) Screen.INNER else Screen.COVER
        LaunchedEffect(screen) {
            ui.screen = screen
            ui.selectedId = null
            Store.lastScreen = screen
        }
        val layout by Store.layout(screen).collectAsState()

        if (s.wallpaperDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.wallpaperDim)))

        val drawerVisible by remember { derivedStateOf { ui.drawerAnim.value > 0.001f } }
        val hPx = constraints.maxHeight.toFloat()

        // Plocha pri otváraní zoznamu jemne ustúpi do pozadia.
        Box(Modifier.fillMaxSize().graphicsLayer {
            val k = ui.drawerAnim.value.coerceIn(0f, 1f)
            scaleX = 1f - 0.08f * k
            scaleY = 1f - 0.08f * k
            alpha = 1f - 0.85f * k
        }) {
            HomeScreen(ui, screen, layout, s)
        }

        if (drawerVisible || ui.drawerOpen) {
            // Zoznam aplikácií vybieha zdola za prstom, so zaoblenými rohmi a pružinou.
            Box(Modifier.fillMaxSize().graphicsLayer {
                val p = ui.drawerAnim.value
                val k = p.coerceIn(0f, 1.05f)
                translationY = (1f - k) * hPx * 0.55f
                alpha = (k * 1.6f).coerceIn(0f, 1f)
                val sc = 0.94f + 0.06f * k.coerceAtMost(1f)
                scaleX = sc; scaleY = sc
                shape = RoundedCornerShape(((1f - k.coerceAtMost(1f)) * 40f).dp)
                clip = p < 0.999f
            }) {
                AppDrawer(ui, s)
            }
        }

        SheetHost(ui, screen, s)
        CrashReport()
        DefaultLauncherPrompt(ui)
    }


    val drawerHalf by remember { derivedStateOf { ui.drawerAnim.value > 0.5f } }
    val overlay = ui.drawerOpen || drawerHalf || (ui.sheet != null && ui.sheet !is Sheet.Folder && ui.sheet !is Sheet.Rename &&
        ui.sheet !is Sheet.RenameItem && ui.sheet != Sheet.RemovePage)
    SideEffect {
        val c = WindowCompat.getInsetsController(ui.activity.window, view)
        val lightBars = overlay && !dark
        c.isAppearanceLightStatusBars = lightBars
        c.isAppearanceLightNavigationBars = lightBars
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (s.hideStatusBar && !overlay) c.hide(WindowInsetsCompat.Type.statusBars())
        else c.show(WindowInsetsCompat.Type.statusBars())
        if (s.hideNavBar && !overlay) c.hide(WindowInsetsCompat.Type.navigationBars())
        else c.show(WindowInsetsCompat.Type.navigationBars())
        val want = if (s.lockPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (ui.activity.requestedOrientation != want) ui.activity.requestedOrientation = want
    }
}

/** Keď launcher nie je predvolený, raz za spustenie ponúkne jeho nastavenie (inak ho Domov nezobrazí). */
@Composable
private fun DefaultLauncherPrompt(ui: LauncherUi) {
    var asked by remember { mutableStateOf(false) }
    val tick = ui.resumeTick
    val isDefault = remember(tick) { ui.activity.isDefaultLauncher() }
    if (isDefault || asked) return
    AlertDialog(
        onDismissRequest = { asked = true },
        title = { Text("Nastaviť Fold Launcher ako domovskú obrazovku?") },
        text = { Text("Kým nie je predvolený, tlačidlo Domov ho neotvorí a systém ho môže kedykoľvek zavrieť.") },
        confirmButton = { TextButton(onClick = { asked = true; ui.activity.requestDefaultLauncher() }) { Text("Nastaviť") } },
        dismissButton = { TextButton(onClick = { asked = true }) { Text("Teraz nie") } },
    )
}

/** Ak launcher naposledy spadol, ukáže výpis chyby, aby sa dal poslať a opraviť. */
@Composable
private fun CrashReport() {
    val ctx = LocalContext.current
    val file = remember { File(ctx.filesDir, App.CRASH_FILE) }
    var text by remember { mutableStateOf(runCatching { if (file.exists()) file.readText() else null }.getOrNull()) }
    val clip = LocalClipboardManager.current
    val t = text ?: return
    fun done() { runCatching { file.delete() }; text = null }
    AlertDialog(
        onDismissRequest = { done() },
        title = { Text("Launcher naposledy spadol") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("Skopíruj výpis a pošli mi ho, opravím to.", style = MaterialTheme.typography.bodyMedium)
                Text(t.take(6000), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { clip.setText(AnnotatedString(t)); done() }) { Text("Kopírovať") } },
        dismissButton = { TextButton(onClick = { done() }) { Text("Zavrieť") } },
    )
}
