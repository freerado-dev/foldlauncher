package sk.fold.launcher.ui

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import sk.fold.launcher.data.Apps
import sk.fold.launcher.data.Cell
import sk.fold.launcher.data.GestureAction
import sk.fold.launcher.data.Grid
import sk.fold.launcher.data.PageEffect
import sk.fold.launcher.data.HomeItem
import sk.fold.launcher.data.ItemType
import sk.fold.launcher.data.Screen
import sk.fold.launcher.data.ScreenLayout
import sk.fold.launcher.data.Settings
import sk.fold.launcher.data.Store
import kotlin.math.roundToInt

@Composable
fun HomeScreen(ui: LauncherUi, screen: Screen, layout: ScreenLayout, s: Settings) {
    val pager = rememberPagerState(pageCount = { layout.pages })
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(pager.currentPage) {
        ui.page = pager.currentPage
        Store.lastPage = pager.currentPage
    }
    LaunchedEffect(ui.homeSignal) { if (ui.homeSignal > 0) pager.animateScrollToPage(0) }
    LaunchedEffect(ui.requestPage, layout.pages) {
        ui.requestPage?.let { p ->
            if (p < layout.pages) { pager.animateScrollToPage(p); ui.requestPage = null }
        }
    }

    val sUp by rememberUpdatedState(s)
    val editing by rememberUpdatedState(ui.editMode)
    val swipe = Modifier.pointerInput(Unit) {
        var total = 0f
        var dir = 0
        var fired = false
        val tracker = VelocityTracker()
        val threshold = 56.dp.toPx()
        detectVerticalDragGestures(
            onDragStart = { total = 0f; dir = 0; fired = false; tracker.resetTracking() },
            onVerticalDrag = { ch, d ->
                if (!editing) {
                    ch.consume()
                    total += d
                    tracker.addPosition(ch.uptimeMillis, ch.position)
                    if (dir == 0 && d != 0f) dir = if (d < 0) -1 else 1
                    val range = size.height * 0.6f
                    if (dir < 0 && sUp.swipeUp == GestureAction.DRAWER) ui.dragDrawer(-total / range)
                    else if (dir > 0 && !fired && total > threshold) { fired = true; ui.gesture(sUp.swipeDown) }
                }
            },
            onDragEnd = {
                if (!editing) {
                    val range = size.height * 0.6f
                    if (dir < 0 && sUp.swipeUp == GestureAction.DRAWER) ui.settleDrawer(-tracker.calculateVelocity().y / range)
                    else if (dir < 0 && total < -threshold) ui.gesture(sUp.swipeUp)
                }
            },
            onDragCancel = { if (dir < 0 && sUp.swipeUp == GestureAction.DRAWER) ui.settleDrawer(0f) },
        )
    }

    // V režime úprav sa stránka zmenší (mriežka ostane rovnaká) a nad/pod ňou sú lišty.
    BoxWithConstraints(Modifier.fillMaxSize().then(swipe).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val h = maxHeight
        val editScale = ((h - 150.dp) / h).coerceIn(0.6f, 0.9f)
        val scale by animateFloatAsState(if (ui.editMode) editScale else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "edit")
        LaunchedEffect(scale) { ui.homeScale = scale }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(bottom = if (layout.pages > 1 && s.showPageDots) 16.dp else 0.dp)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0.42f)
                },
            userScrollEnabled = !dragging,
            beyondViewportPageCount = 1,
        ) { page ->
            val effect = if (ui.editMode) PageEffect.SLIDE else s.pageEffect
            Box(Modifier.fillMaxSize().graphicsLayer {
                val o = (pager.currentPage - page) + pager.currentPageOffsetFraction
                val a = kotlin.math.abs(o).coerceIn(0f, 1f)
                when (effect) {
                    PageEffect.SLIDE -> {}
                    PageEffect.FADE -> { translationX = o * size.width; alpha = 1f - a }
                    PageEffect.ZOOM -> { val sc = 1f - 0.18f * a; scaleX = sc; scaleY = sc; alpha = 1f - 0.6f * a }
                    PageEffect.CUBE -> {
                        cameraDistance = 14f * density
                        transformOrigin = TransformOrigin(if (o > 0) 1f else 0f, 0.5f)
                        rotationY = -90f * o.coerceIn(-1f, 1f)
                    }
                }
            }) {
                HomePage(ui, screen, layout, page, s) { dragging = it }
            }
        }
        if (s.floatAssist) {
            val fp = (if (screen.isCover) s.floatCoverPage else s.floatInnerPage).coerceIn(0, layout.pages - 1)
            val w = constraints.maxWidth.toFloat()
            val hArea = constraints.maxHeight.toFloat()
            // Len na jednej stránke: tlačidlo sa posúva spolu so stránkou pri listovaní.
            Box(Modifier.fillMaxSize().graphicsLayer {
                if (!s.floatAllPages) translationX = (fp - pager.currentPage - pager.currentPageOffsetFraction) * w
            }) {
                FloatingAssistant(ui, screen, s, w, hArea)
            }
        }
        if (layout.pages > 1 && s.showPageDots) Box(Modifier.align(Alignment.BottomCenter)) { PageDots(layout.pages, pager.currentPage) }
        if (ui.editMode) {
            Box(Modifier.align(Alignment.TopCenter)) { SelectionBar(ui, screen, layout, s) }
            Box(Modifier.align(Alignment.BottomCenter)) { EditToolbar(ui, layout) }
        }
    }
}

/** Plávajúce tlačidlo asistenta – nad ikonami a widgetmi, na všetkých stránkach, kdekoľvek mimo mriežky. */
@Composable
private fun FloatingAssistant(ui: LauncherUi, screen: Screen, s: Settings, areaW: Float, areaH: Float) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val sizePx = with(density) { s.floatSize.dp.toPx() }
    val fx = if (screen.isCover) s.floatCoverX else s.floatInnerX
    val fy = if (screen.isCover) s.floatCoverY else s.floatInnerY
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val baseX = fx * (areaW - sizePx)
    val baseY = fy * (areaH - sizePx)
    val curX by rememberUpdatedState(baseX)
    val curY by rememberUpdatedState(baseY)
    val editing by rememberUpdatedState(ui.editMode)
    Box(
        Modifier
            .offset {
                IntOffset(
                    (baseX + drag.x).coerceIn(0f, areaW - sizePx).roundToInt(),
                    (baseY + drag.y).coerceIn(0f, areaH - sizePx).roundToInt(),
                )
            }
            .size(s.floatSize.dp)
            .zIndex(10f)
            .graphicsLayer { if (dragging) { scaleX = 1.12f; scaleY = 1.12f } }
            .pointerInput(screen, areaW, areaH, sizePx) {
                itemGestures(
                    editing = { editing },
                    isWidget = { false },
                    onSelect = {},
                    onTap = { ui.openAssistant() },
                    onLongPress = { buzz(haptic, HapticFeedbackType.LongPress); menu = true },
                    onDragStart = { menu = false; dragging = true; drag = Offset.Zero },
                    onDrag = { d -> drag += d },
                    onDragEnd = {
                        val nx = ((curX + drag.x).coerceIn(0f, areaW - sizePx) / (areaW - sizePx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val ny = ((curY + drag.y).coerceIn(0f, areaH - sizePx) / (areaH - sizePx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        Store.editSettings {
                            if (screen.isCover) it.copy(floatCoverX = nx, floatCoverY = ny)
                            else it.copy(floatInnerX = nx, floatInnerY = ny)
                        }
                        drag = Offset.Zero; dragging = false
                    },
                )
            },
    ) {
        AssistantIcon(s.floatSize.dp)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            HomeMenuItem("Vybrať asistenta", Icons.Filled.Mic) { menu = false; ui.sheet = Sheet.AssistantSettings }
            HomeMenuItem(if (s.floatAllPages) "Len na tejto stránke" else "Na všetkých stránkach", Icons.Filled.Widgets) {
                menu = false
                Store.editSettings {
                    val p = ui.page
                    val n = it.copy(floatAllPages = !it.floatAllPages)
                    if (screen.isCover) n.copy(floatCoverPage = p) else n.copy(floatInnerPage = p)
                }
            }
            HomeMenuItem("Väčšie", Icons.Filled.Add) { Store.editSettings { it.copy(floatSize = (it.floatSize + 8f).coerceAtMost(112f)) } }
            HomeMenuItem("Menšie", Icons.Filled.Remove) { Store.editSettings { it.copy(floatSize = (it.floatSize - 8f).coerceAtLeast(32f)) } }
            HomeMenuItem("Vrátiť do mriežky", Icons.Filled.Apps) {
                menu = false
                Store.editSettings { it.copy(floatAssist = false) }
                ui.addElement(ItemType.ASSISTANT)
            }
            HomeMenuItem("Odstrániť z plochy", Icons.Filled.Delete) { menu = false; Store.editSettings { it.copy(floatAssist = false) } }
        }
    }
}

@Composable
private fun HomeMenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier.padding(horizontal = 3.dp).size(if (i == current) 8.dp else 6.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.45f))
            )
        }
    }
}

private enum class Edge { LEFT, TOP, RIGHT, BOTTOM, CORNER }

@Composable
fun HomePage(ui: LauncherUi, screen: Screen, layout: ScreenLayout, page: Int, s: Settings, onDragging: (Boolean) -> Unit) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val items = layout.items.filter { it.page == page }
    val margin = if (screen.isCover) 14.dp else 28.dp
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = margin, vertical = 2.dp)) {
        // Bunky podľa zvoleného počtu stĺpcov a riadkov – plocha využije celú výšku.
        val pageW = constraints.maxWidth.toFloat()
        val pageH = constraints.maxHeight.toFloat()
        val cw = pageW / layout.cols
        val ch = pageH / layout.rows
        val gridH = pageH
        val top = 0f
        val cwDp = with(density) { cw.toDp() }
        val chDp = with(density) { ch.toDp() }
        LaunchedEffect(cwDp, chDp, screen) {
            ui.cellW = cwDp.value; ui.cellH = chDp.value
            Store.cellDp[screen] = cwDp.value to chDp.value
        }

        var dragId by remember { mutableStateOf<Long?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var resize by remember { mutableStateOf<Cell?>(null) }
        var pressMenu by remember { mutableStateOf<Offset?>(null) }
        val sUp by rememberUpdatedState(s)

        // Pozadie (celá stránka): dvojité ťuknutie, podržanie → menu, ťuknutie v úpravách zruší výber.
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { if (!ui.editMode) ui.gesture(sUp.doubleTap) },
                onLongPress = { pos ->
                    if (!ui.editMode) {
                        buzz(haptic, HapticFeedbackType.LongPress)
                        pressMenu = pos
                    }
                },
                onTap = { if (ui.editMode) ui.selectedId = null },
            )
        })

        pressMenu?.let { pos ->
            Box(Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }.size(1.dp)) {
                DropdownMenu(expanded = true, onDismissRequest = { pressMenu = null }) {
                    HomeMenuItem("Upraviť plochu", Icons.Filled.Edit) { pressMenu = null; ui.startEdit() }
                    HomeMenuItem("Pridať widget", Icons.Filled.Widgets) { pressMenu = null; ui.sheet = Sheet.Widgets }
                    HomeMenuItem("Pridať aplikácie", Icons.Filled.Apps) { pressMenu = null; ui.sheet = Sheet.AddApps }
                    HomeMenuItem("Tapeta", Icons.Filled.Wallpaper) {
                        pressMenu = null
                        runCatching { ui.activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Tapeta")) }
                    }
                    HomeMenuItem("Nastavenia launchera", Icons.Filled.Settings) { pressMenu = null; ui.sheet = Sheet.Settings }
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().height(with(density) { gridH.toDp() })
                .offset { IntOffset(0, top.roundToInt()) }
        ) {
            if (ui.editMode && s.showGrid) {
                Canvas(Modifier.fillMaxSize()) {
                    val line = Color.White.copy(alpha = 0.16f)
                    for (i in 0..layout.cols) drawLine(line, Offset(i * cw, 0f), Offset(i * cw, gridH), 1f)
                    for (j in 0..layout.rows) drawLine(line, Offset(0f, j * ch), Offset(pageW, j * ch), 1f)
                }
            }

            fun targetOf(item: HomeItem): Cell {
                val c = item.cell
                val nx = ((c.x * cw + dragOffset.x) / cw).roundToInt().coerceIn(0, layout.cols - c.w)
                val ny = ((c.y * ch + dragOffset.y) / ch).roundToInt().coerceIn(0, layout.rows - c.h)
                return Cell(nx, ny, c.w, c.h)
            }

            /** Ikona alebo priečinok pod stredom ťahanej ikony – pustením vznikne / doplní sa priečinok. */
            fun folderTargetOf(item: HomeItem, all: List<HomeItem>): HomeItem? {
                if (item.type != ItemType.APP) return null
                val cx = (item.x + item.w / 2f) * cw + dragOffset.x
                val cy = (item.y + item.h / 2f) * ch + dragOffset.y
                return all.firstOrNull { o ->
                    o.id != item.id && o.page == page && (o.type == ItemType.APP || o.type == ItemType.FOLDER) &&
                        cx >= o.x * cw && cx < (o.x + o.w) * cw && cy >= o.y * ch && cy < (o.y + o.h) * ch
                }
            }

            // Náhľad: cieľ presunu, alebo zvýraznenie budúceho priečinka.
            val dragged = items.find { it.id == dragId }
            val folderTarget = dragged?.let { folderTargetOf(it, items) }
            if (folderTarget != null) {
                val c = folderTarget.cell
                Box(
                    Modifier.offset { IntOffset((c.x * cw).roundToInt(), (c.y * ch).roundToInt()) }
                        .size(with(density) { (c.w * cw).toDp() }, with(density) { (c.h * ch).toDp() })
                        .padding(3.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp))
                )
            } else {
                val ghost: Pair<Cell, Boolean>? = when {
                    dragged != null -> targetOf(dragged) to true
                    resize != null && ui.selectedId != null -> resize!! to layout.isFree(page, resize!!, ui.selectedId)
                    else -> null
                }
                ghost?.let { (c, ok) ->
                    Box(
                        Modifier.offset { IntOffset((c.x * cw).roundToInt(), (c.y * ch).roundToInt()) }
                            .size(with(density) { (c.w * cw).toDp() }, with(density) { (c.h * ch).toDp() })
                            .padding(2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background((if (ok) Color.White else Color(0xFFFF453A)).copy(alpha = 0.22f))
                            .border(1.5.dp, (if (ok) Color.White else Color(0xFFFF453A)).copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    )
                }
            }

            // Prvky, ktoré sa po pustení odsunú, jemne zvýrazníme.
            if (dragged != null && folderTarget == null) {
                val t = targetOf(dragged)
                items.filter { it.id != dragged.id && Grid.overlaps(it.cell, t) }.forEach { b ->
                    Box(
                        Modifier.offset { IntOffset((b.x * cw).roundToInt(), (b.y * ch).roundToInt()) }
                            .size(with(density) { (b.w * cw).toDp() }, with(density) { (b.h * ch).toDp() })
                            .padding(3.dp)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                    )
                }
            }

            items.forEach { item ->
                key(item.id) {
                    val selected = ui.editMode && ui.selectedId == item.id
                    val rect = if (selected && resize != null) resize!! else item.cell
                    val isDragged = dragId == item.id
                    ItemFrame(
                        ui = ui, item = item, s = s, rect = rect, cw = cw, ch = ch,
                        dragOffset = if (isDragged) dragOffset else Offset.Zero,
                        dragged = isDragged, selected = selected,
                        onDragStart = {
                            ui.selectedId = item.id
                            dragId = item.id; dragOffset = Offset.Zero; onDragging(true)
                        },
                        onDrag = { d -> dragOffset += d },
                        onDragEnd = { commit ->
                            if (commit) {
                                val cur = Store.layout(screen).value
                                val it2 = cur.items.find { it.id == item.id }
                                if (it2 != null) {
                                    val ft = folderTargetOf(it2, cur.items)
                                    if (ft != null) {
                                        buzz(haptic, HapticFeedbackType.LongPress)
                                        ui.dropIntoFolder(it2, ft)
                                    } else {
                                        val t = targetOf(it2)
                                        if (t != it2.cell) {
                                            // Obsadené miesto → prekážajúce prvky sa automaticky presunú (výmena miest).
                                            if (!cur.isFree(page, t, it2.id)) buzz(haptic, HapticFeedbackType.TextHandleMove)
                                            Store.editLayout(screen) { l -> l.moveWithSwap(it2.id, t) ?: l }
                                        }
                                    }
                                }
                            }
                            dragId = null; dragOffset = Offset.Zero; onDragging(false)
                        },
                    )
                }
            }

            // Úchyty na zmenu veľkosti po bunkách.
            val sel = items.find { it.id == ui.selectedId }
            if (ui.editMode && sel != null && dragId == null) {
                val r = resize ?: sel.cell
                Edge.entries.forEach { edge ->
                    key(sel.id, edge) {
                        ResizeHandle(
                            edge = edge, rect = r, cw = cw, ch = ch,
                            pageW = pageW, pageH = gridH,
                            onStart = { resize = sel.cell; onDragging(true) },
                            onResize = { acc ->
                                val st = sel.cell
                                val dx = (acc.x / cw).roundToInt()
                                val dy = (acc.y / ch).roundToInt()
                                val n = when (edge) {
                                    Edge.LEFT -> { val nx = (st.x + dx).coerceIn(0, st.x + st.w - 1); Cell(nx, st.y, st.x + st.w - nx, st.h) }
                                    Edge.TOP -> { val ny = (st.y + dy).coerceIn(0, st.y + st.h - 1); Cell(st.x, ny, st.w, st.y + st.h - ny) }
                                    Edge.RIGHT -> st.copy(w = (st.w + dx).coerceIn(1, layout.cols - st.x))
                                    Edge.BOTTOM -> st.copy(h = (st.h + dy).coerceIn(1, layout.rows - st.y))
                                    Edge.CORNER -> st.copy(w = (st.w + dx).coerceIn(1, layout.cols - st.x), h = (st.h + dy).coerceIn(1, layout.rows - st.y))
                                }
                                if (n != resize) buzz(haptic, HapticFeedbackType.TextHandleMove)
                                resize = n
                            },
                            onEnd = {
                                val n = resize
                                if (n != null && n != sel.cell) {
                                    if (Store.layout(screen).value.isFree(page, n, sel.id)) Store.editLayout(screen) { l -> l.update(sel.id) { it.at(n) } }
                                    else buzz(haptic, HapticFeedbackType.LongPress)
                                }
                                resize = null; onDragging(false)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(edge: Edge, rect: Cell, cw: Float, ch: Float, pageW: Float, pageH: Float, onStart: () -> Unit, onResize: (Offset) -> Unit, onEnd: () -> Unit) {
    val density = LocalDensity.current
    val touch = 36.dp
    val tPx = with(density) { touch.toPx() }
    val left = rect.x * cw
    val top = rect.y * ch
    val right = (rect.x + rect.w) * cw
    val bottom = (rect.y + rect.h) * ch
    val (rx, ry) = when (edge) {
        Edge.LEFT -> left to (top + bottom) / 2
        Edge.TOP -> (left + right) / 2 to top
        Edge.RIGHT -> right to (top + bottom) / 2
        Edge.BOTTOM -> (left + right) / 2 to bottom
        Edge.CORNER -> right to bottom
    }
    // Úchyt nikdy nevytŕča mimo obrazovku – aj pri prvku pri okraji displeja sa dá chytiť.
    val cx = rx.coerceIn(tPx / 2, maxOf(tPx / 2, pageW - tPx / 2))
    val cy = ry.coerceIn(tPx / 2, maxOf(tPx / 2, pageH - tPx / 2))
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onResize)
    val end by rememberUpdatedState(onEnd)
    Box(
        Modifier.offset { IntOffset((cx - tPx / 2).roundToInt(), (cy - tPx / 2).roundToInt()) }
            .size(touch).zIndex(5f)
            .pointerInput(edge) {
                var acc = Offset.Zero
                detectDragGestures(
                    onDragStart = { acc = Offset.Zero; start() },
                    onDrag = { c, d -> c.consume(); acc += d; move(acc) },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(if (edge == Edge.CORNER) 18.dp else 14.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

@Composable
private fun ItemFrame(
    ui: LauncherUi, item: HomeItem, s: Settings, rect: Cell, cw: Float, ch: Float,
    dragOffset: Offset, dragged: Boolean, selected: Boolean,
    onDragStart: () -> Unit, onDrag: (Offset) -> Unit, onDragEnd: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val wDp = with(density) { (rect.w * cw).toDp() }
    val hDp = with(density) { (rect.h * ch).toDp() }

    val editing by rememberUpdatedState(ui.editMode)
    val itemNow by rememberUpdatedState(item)
    val dStart by rememberUpdatedState(onDragStart)
    val dMove by rememberUpdatedState(onDrag)
    val dEnd by rememberUpdatedState(onDragEnd)

    Box(
        Modifier
            .offset { IntOffset((rect.x * cw).roundToInt(), (rect.y * ch).roundToInt()) }
            .size(wDp, hDp)
            .zIndex(if (dragged) 3f else if (selected) 1f else 0f)
            .pointerInput(item.id) {
                itemGestures(
                    editing = { editing },
                    isWidget = { itemNow.type == ItemType.WIDGET },
                    onSelect = { ui.selectedId = itemNow.id },
                    onTap = { ui.tap(itemNow) },
                    onLongPress = {
                        buzz(haptic, HapticFeedbackType.LongPress)
                        menu = true
                    },
                    onDragStart = {
                        menu = false
                        if (!ui.editMode) ui.startEdit(itemNow.id)
                        if (ui.editMode) dStart()
                    },
                    onDrag = { if (ui.editMode) dMove(it) },
                    onDragEnd = { dEnd(ui.editMode) },
                )
            }
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .graphicsLayer {
                if (dragged) { scaleX = 1.05f; scaleY = 1.05f; alpha = 0.92f }
            }
    ) {
        val widgetDrag = remember(item.id) {
            WidgetDrag(
                start = { menu = false; if (ui.editMode) dStart() },
                move = { d -> if (ui.editMode) dMove(d / ui.homeScale) },
                end = { ok -> dEnd(ok && ui.editMode) },
            )
        }
        ItemContent(ui, item, s, wDp, hDp, widgetDrag)
        if (ui.editMode) {
            // V režime úprav zachytíme dotyky skôr, než sa dostanú k obsahu (widgetu).
            Box(
                Modifier.fillMaxSize()
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            )
        }
        DropdownMenu(expanded = menu && !ui.editMode, onDismissRequest = { menu = false }) {
            ItemMenu(ui, item) { menu = false }
        }
    }
}

/** Ťuknutie / dlhé podržanie / ťahanie bez reštartu gesta pri prepnutí do režimu úprav. */
private suspend fun PointerInputScope.itemGestures(
    editing: () -> Boolean,
    isWidget: () -> Boolean,
    onSelect: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val slop = viewConfiguration.touchSlop
    if (editing()) {
        onSelect()
        var dragging = false
        var total = Offset.Zero
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            val d = c.positionChange()
            total += d
            if (!dragging && total.getDistance() > slop) { dragging = true; onDragStart() }
            if (dragging) { c.consume(); onDrag(d) }
        }
        if (dragging) onDragEnd()
        return@awaitEachGesture
    }
    if (isWidget()) return@awaitEachGesture

    var up = false
    val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val ev = awaitPointerEvent()
            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) { up = !c.isConsumed; break }
            if (c.isConsumed || (c.position - down.position).getDistance() > slop) break
        }
        true
    }
    if (finished != null) {
        if (up) {
            currentEvent.changes.forEach { it.consume() }
            onTap()
        }
        return@awaitEachGesture
    }
    // Dlhé podržanie.
    onLongPress()
    var dragging = false
    var total = Offset.Zero
    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
        if (!c.pressed) { c.consume(); break }
        val d = c.positionChange()
        total += d
        c.consume()
        if (!dragging && total.getDistance() > slop * 1.5f) { dragging = true; onDragStart() }
        if (dragging) onDrag(d)
    }
    if (dragging) onDragEnd()
}

@Composable
private fun SelectionBar(ui: LauncherUi, screen: Screen, layout: ScreenLayout, s: Settings) {
    val sel = layout.items.find { it.id == ui.selectedId }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
    ) {
        if (sel == null) {
            Text(
                "Ťukni na prvok · ťahaj ho · úchytmi meníš veľkosť po bunkách (${layout.cols}×${layout.rows})",
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${itemTitle(sel, s)} · ${sel.w}×${sel.h}",
                    Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                )
                BarButton(Icons.AutoMirrored.Filled.ArrowBack, "Stránka") { ui.moveToPage(sel, sel.page - 1) }
                BarButton(Icons.AutoMirrored.Filled.ArrowForward, "Stránka") { ui.moveToPage(sel, sel.page + 1) }
                if (sel.type == ItemType.FOLDER) BarButton(Icons.Filled.Apps, "Obsah") { ui.sheet = Sheet.FolderApps(sel.id) }
                if (sel.type == ItemType.FAVORITES) BarButton(Icons.Filled.Apps, "Obľúbené") { ui.sheet = Sheet.Favorites }
                if (sel.type == ItemType.FOLDER || sel.type == ItemType.SHORTCUT || sel.type == ItemType.APP)
                    BarButton(Icons.Filled.Edit, "Názov") { ui.sheet = Sheet.RenameItem(sel.id) }
                BarButton(Icons.Filled.Delete, "Odstrániť", Color(0xFFFF453A)) { ui.remove(sel) }
            }
        }
    }
}

fun itemTitle(i: HomeItem, s: Settings): String = when (i.type) {
    ItemType.APP -> i.label ?: Apps.find(i.app)?.let { sk.fold.launcher.data.labelOf(it, s) } ?: "Aplikácia"
    ItemType.SHORTCUT -> i.label ?: "Skratka"
    ItemType.WIDGET -> "Widget"
    ItemType.FOLDER -> i.label ?: "Priečinok"
    ItemType.CLOCK -> "Hodiny"
    ItemType.FAVORITES -> "Obľúbené"
    ItemType.SEARCH -> "Vyhľadávanie"
    ItemType.ASSISTANT -> "Asistent"
}

@Composable
private fun BarButton(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 10.sp, color = tint)
    }
}

@Composable
private fun EditToolbar(ui: LauncherUi, layout: ScreenLayout) {
    val act = ui.activity
    Surface(
        Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(Icons.Filled.Widgets, "Widgety") { ui.sheet = Sheet.Widgets }
            BarButton(Icons.Filled.Apps, "Aplikácie") { ui.sheet = Sheet.AddApps }
            BarButton(Icons.Filled.Add, "Stránka") {
                Store.editLayout(ui.screen) { it.addPage() }
                ui.showPage(layout.pages)
            }
            BarButton(Icons.Filled.Remove, "Stránka") {
                if (layout.items.any { it.page == ui.page }) ui.sheet = Sheet.RemovePage
                else Store.editLayout(ui.screen) { it.removePage(ui.page) }
            }
            BarButton(Icons.Filled.Wallpaper, "Tapeta") {
                runCatching { act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Tapeta")) }
            }
            BarButton(Icons.Filled.Settings, "Nastavenia") { ui.sheet = Sheet.Settings }
            Spacer(Modifier.width(6.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary) {
                Row(
                    Modifier.pointerInput(Unit) { detectTapGestures { ui.stopEdit() } }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Hotovo", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

