package sk.fold.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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

        HomeScreen(ui, screen, layout, s)

        AnimatedVisibility(
            visible = ui.drawerOpen,
            enter = slideInVertically { it / 4 } + fadeIn(),
            exit = slideOutVertically { it / 4 } + fadeOut(),
        ) {
            AppDrawer(ui, s)
        }

        SheetHost(ui, screen, s)
    }


    val overlay = ui.drawerOpen || (ui.sheet != null && ui.sheet !is Sheet.Folder && ui.sheet !is Sheet.Rename &&
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
