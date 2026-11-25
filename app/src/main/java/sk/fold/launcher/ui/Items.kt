package sk.fold.launcher.ui

import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sk.fold.launcher.data.AppInfo
import sk.fold.launcher.data.Apps
import sk.fold.launcher.data.BadgeStyle
import sk.fold.launcher.data.Badges
import sk.fold.launcher.data.ClockStyle
import sk.fold.launcher.data.HomeItem
import sk.fold.launcher.data.IconCache
import sk.fold.launcher.data.ItemType
import sk.fold.launcher.data.LauncherWidgetView
import sk.fold.launcher.data.Settings
import sk.fold.launcher.data.Widgets
import sk.fold.launcher.data.labelOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun labelStyle(s: Settings, size: TextUnit, color: Color = Color.White) = TextStyle(
    color = color,
    fontSize = size,
    shadow = if (s.labelShadow) Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1.5f), 5f) else null,
)

@Composable
fun AppIconImage(a: AppInfo, size: Dp, s: Settings, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dark = LocalDark.current
    val px = IconCache.bucket(with(LocalDensity.current) { size.roundToPx() })
    val k = IconCache.key(a.key, px, s, dark)
    val bmp by produceState<ImageBitmap?>(IconCache.cached(k), k) {
        if (value == null) value = withContext(Dispatchers.IO) { runCatching { IconCache.app(ctx, a, px, s, dark) }.getOrNull() }
    }
    val b = bmp
    if (b != null) Image(b, null, modifier.size(size)) else Spacer(modifier.size(size))
}

@Composable
fun ShortcutIconImage(item: HomeItem, size: Dp, s: Settings) {
    val ctx = LocalContext.current
    val dark = LocalDark.current
    val px = IconCache.bucket(with(LocalDensity.current) { size.roundToPx() })
    val bmp by produceState<ImageBitmap?>(null, item.id, px, s.iconShape, dark) {
        value = withContext(Dispatchers.IO) { runCatching { IconCache.shortcut(ctx, item, px, s, dark) }.getOrNull() }
    }
    val b = bmp
    if (b != null) Image(b, null, Modifier.size(size))
    else Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
}

@Composable
fun BadgeMark(count: Int, s: Settings, modifier: Modifier = Modifier) {
    if (count <= 0 || s.badges == BadgeStyle.NONE) return
    val c = MaterialTheme.colorScheme.primary
    if (s.badges == BadgeStyle.DOT) {
        Box(modifier.size(11.dp).clip(CircleShape).background(Color.White).padding(1.5.dp).clip(CircleShape).background(c))
    } else {
        Box(modifier.clip(CircleShape).background(c).padding(horizontal = 5.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
            Text(if (count > 99) "99+" else "$count", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ItemContent(ui: LauncherUi, item: HomeItem, s: Settings, wDp: Dp, hDp: Dp) {
    when (item.type) {
        ItemType.APP -> {
            val apps by Apps.list.collectAsState()
            val a = remember(apps, item.app) { apps.firstOrNull { it.key == item.app } }
            val notifs by Badges.state.collectAsState()
            if (a != null) IconTile(item.label ?: labelOf(a, s), s, wDp, hDp, notifs[a.pkg]?.count ?: 0) { sz -> AppIconImage(a, sz, s) }
            else IconTile("?", s, wDp, hDp, 0) { sz -> Box(Modifier.size(sz).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) }
        }
        ItemType.SHORTCUT -> IconTile(item.label ?: "", s, wDp, hDp, 0) { sz -> ShortcutIconImage(item, sz, s) }
        ItemType.FOLDER -> IconTile(item.label ?: "Priečinok", s, wDp, hDp, 0) { sz -> FolderIcon(item, sz, s) }
        ItemType.WIDGET -> WidgetBox(ui, item, wDp, hDp)
        ItemType.CLOCK -> ClockView(s, wDp, hDp)
        ItemType.FAVORITES -> FavoritesView(ui, s, hDp)
        ItemType.SEARCH -> SearchPill(s, hDp)
    }
}

@Composable
private fun IconTile(label: String, s: Settings, wDp: Dp, hDp: Dp, badge: Int, icon: @Composable (Dp) -> Unit) {
    val labelH = (s.labelSize * 1.45f + 4f).dp
    val showLabel = s.showLabels && hDp - labelH >= 28.dp
    val avail = if (showLabel) minOf(wDp, hDp - labelH) else minOf(wDp, hDp)
    val iconSize = (avail * s.iconScale).coerceAtLeast(16.dp)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box {
            icon(iconSize)
            BadgeMark(badge, s, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
        }
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(
                label, style = labelStyle(s, s.labelSize.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, fontFamily = LocalFont.current,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun FolderIcon(item: HomeItem, size: Dp, s: Settings) {
    val apps by Apps.list.collectAsState()
    val inside = remember(apps, item.apps) { item.apps.mapNotNull { k -> apps.firstOrNull { it.key == k } }.take(4) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Color.White.copy(alpha = 0.28f)).padding(size * 0.12f),
    ) {
        val mini = size * 0.34f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            for (row in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0..1) {
                    val a = inside.getOrNull(row * 2 + col)
                    if (a != null) AppIconImage(a, mini, s) else Spacer(Modifier.size(mini))
                }
            }
        }
    }
}

@Composable
private fun WidgetBox(ui: LauncherUi, item: HomeItem, wDp: Dp, hDp: Dp) {
    val ctx = LocalContext.current
    val info = remember(item.widgetId) { runCatching { Widgets.manager.getAppWidgetInfo(item.widgetId) }.getOrNull() }
    if (info == null) {
        Box(
            Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Widgets, null, tint = Color.White)
                Text("Widget nie je dostupný", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
        return
    }
    val view = remember(item.widgetId) {
        Widgets.host.createView(ctx, item.widgetId, info) as? LauncherWidgetView
    }
    if (view == null) return
    val onLong by rememberUpdatedState { ui.startEdit(item.id) }
    AndroidView(
        factory = { (view.parent as? ViewGroup)?.removeView(view); view },
        modifier = Modifier.fillMaxSize(),
        update = { v -> v.onLongPress = { onLong() } },
    )
    LaunchedEffect(view, wDp, hDp) { Widgets.updateSize(view, wDp.value, hDp.value) }
}

@Composable
private fun ClockView(s: Settings, wDp: Dp, hDp: Dp) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    val density = LocalDensity.current
    val ff = LocalFont.current
    val time = remember(now, s.clock24) { SimpleDateFormat(if (s.clock24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 60000) { SimpleDateFormat("EEEE d. MMMM", Locale.getDefault()).format(Date(now)).replaceFirstChar { it.uppercase() } }
    fun sp(d: Dp) = with(density) { d.toSp() }
    Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
        when (s.clockStyle) {
            ClockStyle.LARGE -> Column {
                val tSize = minOf(if (s.clockDate) hDp * 0.6f else hDp * 0.82f, wDp * 0.3f)
                Text(time, style = labelStyle(s, sp(tSize)), fontWeight = FontWeight.Light, fontFamily = ff, maxLines = 1)
                if (s.clockDate) Text(date, style = labelStyle(s, sp(minOf(hDp * 0.14f, 22.dp))), fontFamily = ff, maxLines = 1)
            }
            ClockStyle.STACKED -> Column {
                val parts = time.split(":")
                val tSize = minOf(if (s.clockDate) hDp * 0.36f else hDp * 0.44f, wDp * 0.5f)
                Text(parts.getOrElse(0) { "" }, style = labelStyle(s, sp(tSize)), fontWeight = FontWeight.Bold, fontFamily = ff, lineHeight = sp(tSize))
                Text(parts.getOrElse(1) { "" }, style = labelStyle(s, sp(tSize), MaterialTheme.colorScheme.primary), fontWeight = FontWeight.Bold, fontFamily = ff, lineHeight = sp(tSize))
                if (s.clockDate) Text(date, style = labelStyle(s, sp(minOf(hDp * 0.11f, 18.dp))), fontFamily = ff, maxLines = 1)
            }
            ClockStyle.MINIMAL -> Row(verticalAlignment = Alignment.CenterVertically) {
                val tSize = minOf(hDp * 0.5f, wDp * 0.16f)
                Text(time, style = labelStyle(s, sp(tSize)), fontWeight = FontWeight.Medium, fontFamily = ff)
                if (s.clockDate) {
                    Spacer(Modifier.width(10.dp))
                    Text(date, style = labelStyle(s, sp(tSize * 0.42f)), fontFamily = ff, maxLines = 2)
                }
            }
        }
    }
}

/** Zoznam obľúbených v štýle Niagara – ikona, názov a posledná notifikácia. */
@Composable
private fun FavoritesView(ui: LauncherUi, s: Settings, hDp: Dp) {
    val apps by Apps.list.collectAsState()
    val notifs by Badges.state.collectAsState()
    val favs = remember(apps, s.favorites) { s.favorites.mapNotNull { k -> apps.firstOrNull { it.key == k } } }
    val ff = LocalFont.current
    if (favs.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(
                "Obľúbené sú prázdne.\nV zozname aplikácií podrž aplikáciu → Do obľúbených.",
                style = labelStyle(s, 13.sp), textAlign = TextAlign.Center, fontFamily = ff,
            )
        }
        return
    }
    val rowH = (hDp / favs.size).coerceIn(40.dp, 76.dp)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        favs.forEach { a ->
            val n = notifs[a.pkg]
            Row(
                Modifier.fillMaxWidth().height(rowH).clip(RoundedCornerShape(14.dp))
                    .clickable { Apps.launch(ui.activity, a) }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    AppIconImage(a, rowH * 0.6f, s)
                    BadgeMark(n?.count ?: 0, s, Modifier.align(Alignment.TopEnd))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        labelOf(a, s), style = labelStyle(s, (rowH.value * 0.3f).coerceIn(14f, 24f).sp),
                        fontFamily = ff, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val line = if (s.favNotifText && n != null) listOfNotNull(n.title, n.text).joinToString(" · ") else ""
                    if (line.isNotEmpty()) Text(
                        line, style = labelStyle(s, (rowH.value * 0.19f).coerceIn(11f, 15f).sp, Color.White.copy(alpha = 0.85f)),
                        fontFamily = ff, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPill(s: Settings, hDp: Dp) {
    val h = hDp.coerceAtMost(56.dp) - 6.dp
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().height(h.coerceAtLeast(28.dp)).clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text("Hľadať", style = labelStyle(s, 16.sp), fontFamily = LocalFont.current)
        }
    }
}

