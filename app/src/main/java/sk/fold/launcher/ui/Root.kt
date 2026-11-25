package sk.fold.launcher.ui

import androidx.activity.compose.BackHandler
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
    }


    val drawerHalf by remember { derivedStateOf { ui.drawerAnim.value > 0.5f } }
    val overlay = ui.drawerOpen || drawerHalf || (ui.sheet != null && ui.sheet !is Sheet.Folder && ui.sheet !is Sheet.Rename &&
        ui.sheet !is Sheet.RenameItem && ui.sheet != Sheet.RemovePage)
    SideEffect {
        val c = WindowCompat.getInsetsController(ui.activity.window, view)
        val lightBars = overlay && !dark
        c.isAppearanceLightStatusBars = lightBars
        c.isAppearanceLightNavigationBars = lightBars
        if (s.hideStatusBar && !overlay) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.statusBars())
        } else c.show(WindowInsetsCompat.Type.statusBars())
    }
}
