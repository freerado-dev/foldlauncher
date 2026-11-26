package sk.fold.launcher.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import sk.fold.launcher.data.FeedItem
import sk.fold.launcher.data.LauncherWidgetView
import sk.fold.launcher.data.Settings
import sk.fold.launcher.data.Store
import sk.fold.launcher.data.Widgets

/** Feed – stránka naľavo od plochy so zvislým zoznamom widgetov cez celú šírku. */
@Composable
fun FeedPage(ui: LauncherUi, s: Settings) {
    val feed by Store.feed.collectAsState()
    val state = rememberLazyListState()
    val margin = if (ui.screen.isCover) 14.dp else 28.dp
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().padding(horizontal = margin),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Feed", style = labelStyle(s, 30.sp), fontWeight = FontWeight.Medium, fontFamily = LocalFont.current, modifier = Modifier.weight(1f))
                IconButton(onClick = { ui.sheet = Sheet.FeedWidgets }) {
                    Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.22f)).padding(6.dp)) {
                        Icon(Icons.Filled.Add, "Pridať widget", tint = Color.White)
                    }
                }
            }
        }
        if (feed.isEmpty()) item(key = "empty") {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Widgets, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(8.dp))
                Text(
                    "Feed je prázdny.\nTlačidlom + pridáš widgety pod seba.\nPodržaním widgetu zmeníš jeho výšku alebo poradie.",
                    style = labelStyle(s, 15.sp, Color.White.copy(alpha = 0.85f)), textAlign = TextAlign.Center,
                )
            }
        }
        items(feed, key = { it.id }) { f -> FeedWidget(ui, f, state, feed) }
    }
}

@Composable
private fun FeedWidget(ui: LauncherUi, f: FeedItem, list: LazyListState, all: List<FeedItem>) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val fling = ScrollableDefaults.flingBehavior()
    var menu by remember { mutableStateOf(false) }
    val info = remember(f.widgetId) { runCatching { Widgets.manager.getAppWidgetInfo(f.widgetId) }.getOrNull() }
    BoxWithConstraints(Modifier.fillMaxWidth().height(f.height.dp).clip(RoundedCornerShape(22.dp))) {
        val w = maxWidth
        if (info == null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                Text("Widget nie je dostupný – podrž pre odstránenie", color = Color.White, fontSize = 13.sp)
            }
        } else {
            val view = remember(f.widgetId) { Widgets.host.createView(ctx, f.widgetId, info) as? LauncherWidgetView }
            if (view != null) {
                AndroidView(
                    factory = { (view.parent as? ViewGroup)?.removeView(view); view },
                    modifier = Modifier.fillMaxSize(),
                    update = { v ->
                        v.onLongPress = { menu = true }
                        // zvislé ťahanie cez widget posúva feed, vodorovné listuje stránky
                        v.onScrollDelta = { dy -> list.dispatchRawDelta(-dy) }
                        v.onScrollEnd = { vel -> scope.launch { list.scroll { with(fling) { performFling(-vel) } } } }
                        v.onPageSwipe = { dir -> ui.swipePage?.invoke(dir) }
                    },
                )
                LaunchedEffect(view, w, f.height) { Widgets.updateSize(view, w.value, f.height) }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Vyšší") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = {
                Store.editFeed { l -> l.map { if (it.id == f.id) it.copy(height = (it.height + 48f).coerceAtMost(900f)) else it } }
            })
            DropdownMenuItem(text = { Text("Nižší") }, leadingIcon = { Icon(Icons.Filled.Remove, null) }, onClick = {
                Store.editFeed { l -> l.map { if (it.id == f.id) it.copy(height = (it.height - 48f).coerceAtLeast(80f)) else it } }
            })
            val i = all.indexOfFirst { it.id == f.id }
            if (i > 0) DropdownMenuItem(text = { Text("Posunúť hore") }, leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) }, onClick = {
                menu = false; Store.editFeed { l -> l.toMutableList().also { m -> val t = m[i]; m[i] = m[i - 1]; m[i - 1] = t } }
            })
            if (i in 0 until all.size - 1) DropdownMenuItem(text = { Text("Posunúť dole") }, leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) }, onClick = {
                menu = false; Store.editFeed { l -> l.toMutableList().also { m -> val t = m[i]; m[i] = m[i + 1]; m[i + 1] = t } }
            })
            DropdownMenuItem(text = { Text("Odstrániť z feedu") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                menu = false
                Store.editFeed { l -> l.filterNot { it.id == f.id } }
                Widgets.release(f.widgetId, f.id)
            })
        }
    }
}

