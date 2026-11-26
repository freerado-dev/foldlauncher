@file:OptIn(ExperimentalLayoutApi::class)

package sk.fold.launcher.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.fold.launcher.data.ACCENTS
import sk.fold.launcher.data.FOLDER_COLORS
import sk.fold.launcher.data.AnimSpeed
import sk.fold.launcher.data.DrawerSort
import sk.fold.launcher.data.PageEffect
import sk.fold.launcher.data.Apps
import sk.fold.launcher.data.BadgeStyle
import sk.fold.launcher.data.Badges
import sk.fold.launcher.data.ClockStyle
import sk.fold.launcher.data.DrawerStyle
import sk.fold.launcher.data.FontChoice
import sk.fold.launcher.data.GestureAction
import sk.fold.launcher.data.IconPacks
import sk.fold.launcher.data.IconShape
import sk.fold.launcher.data.LockService
import sk.fold.launcher.data.Screen
import sk.fold.launcher.data.Settings
import sk.fold.launcher.data.Store
import sk.fold.launcher.data.ThemeMode
import sk.fold.launcher.data.Widgets
import sk.fold.launcher.data.labelOf
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SectionTitle(t: String) {
    Text(
        t, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Column(Modifier.padding(vertical = 4.dp)) { content() } }
}

@Composable
private fun ClickRow(title: String, sub: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Posuvník – hodnota sa zobrazuje počas ťahania, uloží sa po pustení. */
@Composable
private fun SliderRow(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    format: (Float) -> String, onDone: (Float) -> Unit,
) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(format(v), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = v, onValueChange = { v = it }, valueRange = range, steps = steps, onValueChangeFinished = { onDone(v) })
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
            }
        }
    }
}

private fun edit(f: (Settings) -> Settings) = Store.editSettings(f)

@Composable
fun SettingsScreen(ui: LauncherUi, screen: Screen, onClose: () -> Unit) {
    var sub by remember { mutableStateOf<String?>(null) }
    BackHandler { if (sub != null) sub = null else onClose() }
    when (sub) {
        "fav" -> { FavoritesEditor(ui) { sub = null }; return }
        "hidden" -> { HiddenApps { sub = null }; return }
        "pack" -> { IconPackPicker { sub = null }; return }
        "clock" -> { ClockSettingsPage(ui) { sub = null }; return }
        "voice" -> { AssistantPage(ui) { sub = null }; return }
    }
    val s by Store.settings.collectAsState()
    val cover by Store.layout(Screen.COVER).collectAsState()
    val inner by Store.layout(Screen.INNER).collectAsState()
    val ctx = LocalContext.current
    val act = ui.activity
    val tick = ui.resumeTick
    val isDefault = remember(tick) { act.isDefaultLauncher() }
    val badgesOn = remember(tick) { Badges.enabled(ctx) }
    val a11yOn = remember(tick) { LockService.instance != null }
    var confirmCopy by remember { mutableStateOf<Pair<Screen, Screen>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    FullSheet("Nastavenia", onClose = onClose) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (!isDefault) item {
                Card(
                    Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Fold Launcher nie je predvolený", fontWeight = FontWeight.SemiBold)
                        Text("Nastav ho ako domovskú aplikáciu, aby fungovalo tlačidlo Domov, skratky a widgety.",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = { act.requestDefaultLauncher() }) { Text("Nastaviť ako predvolený") }
                    }
                }
            }

            if (!badgesOn || !a11yOn) {
                item { SectionTitle("Povolenia") }
                item {
                    Group {
                        Text(
                            "Samsung blokuje tieto povolenia pre aplikácie mimo Obchodu Play (sivý prepínač alebo hláška " +
                                "„Aplikácii bol zamietnutý prístup“). Postup:\n" +
                                "1) Vypni Automatický blokovač: Nastavenia telefónu → Zabezpečenie a súkromie → Automatický blokovač.\n" +
                                "2) Informácie o aplikácii (nižšie) → ⋮ vpravo hore → Povoliť obmedzené nastavenia.\n" +
                                "3) Zapni prístup k notifikáciám a službu gest.\n" +
                                "4) Automatický blokovač môžeš potom zase zapnúť.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        )
                        ClickRow("Automatický blokovač", "Otvorí nastavenia zabezpečenia telefónu") {
                            val ok = runCatching { act.startActivity(Intent("com.samsung.android.sm.ACTION_AUTO_BLOCKER")) }.isSuccess ||
                                runCatching { act.startActivity(Intent(AndroidSettings.ACTION_SECURITY_SETTINGS)) }.isSuccess
                            if (!ok) act.toast("Otvor Nastavenia → Zabezpečenie a súkromie → Automatický blokovač")
                        }
                        ClickRow("Informácie o aplikácii", "⋮ vpravo hore → Povoliť obmedzené nastavenia") {
                            runCatching {
                                act.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.packageName)))
                            }
                        }
                        if (!badgesOn) ClickRow("Prístup k notifikáciám", "Odznaky a text notifikácií v obľúbených") { openListenerSettings(act) }
                        if (!a11yOn) ClickRow("Služba gest (Dostupnosť)", "Zamknutie dvojitým ťuknutím, otvorenie hornej lišty") { openA11ySettings(act) }
                    }
                }
            }
            if (!a11yOn) item {
                Group {
                    val adminOn = remember(tick) { sk.fold.launcher.data.SysActions.adminActive(act) }
                    ClickRow(
                        "Náhradné zamykanie (správca zariadenia)",
                        if (adminOn) "Zapnuté – dvojité ťuknutie zamkne obrazovku"
                        else "Funguje aj bez služby gest. Po zamknutí môže prvé odomknutie vyžadovať PIN namiesto odtlačku.",
                    ) { if (!adminOn) sk.fold.launcher.data.SysActions.requestAdmin(act) }
                }
            }

            item { SectionTitle("Plocha – mriežka") }
            item {
                Group {
                    Text(
                        "Aktuálne: ${if (screen == Screen.COVER) "vonkajší" else "vnútorný"} displej. Každý displej má vlastné rozloženie. " +
                            "Mriežka vyplní celú výšku plochy. Viac riadkov = lepšie využitie výšky. Pri zmene hustoty sa prvky prepočítajú pomerne.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                    SliderRow("Vonkajší displej – stĺpce", cover.cols.toFloat(), 3f..16f, 12, { "${it.roundToInt()}" }) { v ->
                        Store.editLayout(Screen.COVER) { it.rescale(v.roundToInt(), it.rows) }
                    }
                    SliderRow("Vonkajší displej – riadky", cover.rows.toFloat(), 4f..30f, 25, { "${it.roundToInt()}" }) { v ->
                        Store.editLayout(Screen.COVER) { it.rescale(it.cols, v.roundToInt()) }
                    }
                    SliderRow("Vnútorný displej – stĺpce", inner.cols.toFloat(), 4f..28f, 23, { "${it.roundToInt()}" }) { v ->
                        Store.editLayout(Screen.INNER) { it.rescale(v.roundToInt(), it.rows) }
                    }
                    SliderRow("Vnútorný displej – riadky", inner.rows.toFloat(), 4f..28f, 23, { "${it.roundToInt()}" }) { v ->
                        Store.editLayout(Screen.INNER) { it.rescale(it.cols, v.roundToInt()) }
                    }
                    SwitchRow("Zobraziť mriežku pri úpravách", checked = s.showGrid) { v -> edit { it.copy(showGrid = v) } }
                    SwitchRow("Zamknúť rozloženie", "Zabráni náhodnému presunu", s.lockLayout) { v -> edit { it.copy(lockLayout = v) } }
                    ChoiceRow("Otváranie priečinkov", listOf(false, true), s.folderSheet, { if (it) "Panel zdola" else "Okno" }) { v ->
                        edit { it.copy(folderSheet = v) }
                    }
                    SwitchRow("Nové aplikácie pridať na plochu", "Po inštalácii sa ikona pridá automaticky", s.autoAddApps) { v -> edit { it.copy(autoAddApps = v) } }
                    ChoiceRow("Efekt listovania stránok", PageEffect.entries, s.pageEffect, { it.label }) { v -> edit { it.copy(pageEffect = v) } }
                    SwitchRow("Indikátor stránok", checked = s.showPageDots) { v -> edit { it.copy(showPageDots = v) } }
                    ClickRow("Upraviť plochu", "Pridávanie widgetov, presun a zmena veľkosti") { onClose(); ui.startEdit() }
                    ClickRow("Skopírovať vonkajší → vnútorný") { confirmCopy = Screen.COVER to Screen.INNER }
                    ClickRow("Skopírovať vnútorný → vonkajší") { confirmCopy = Screen.INNER to Screen.COVER }
                }
            }

            item { SectionTitle("Vzhľad plochy") }
            item {
                Group {
                    SwitchRow("Skryť stavový riadok", checked = s.hideStatusBar) { v -> edit { it.copy(hideStatusBar = v) } }
                    SwitchRow("Skryť navigačnú lištu", "Zobrazí sa potiahnutím od spodného okraja", s.hideNavBar) { v -> edit { it.copy(hideNavBar = v) } }
                    SwitchRow("Uzamknúť na výšku", "Plocha sa nebude otáčať", s.lockPortrait) { v -> edit { it.copy(lockPortrait = v) } }
                    SliderRow("Stmavenie tapety", s.wallpaperDim, 0f..0.8f, 0, { "${(it * 100).roundToInt()} %" }) { v -> edit { it.copy(wallpaperDim = v) } }
                    ClickRow("Zmeniť tapetu") {
                        runCatching { act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Tapeta")) }
                    }
                    ClickRow("Hodiny a počasie", "Štýl, počasie, aplikácie po ťuknutí") { sub = "clock" }
                }
            }

            item { SectionTitle("Ikony") }
            item {
                Group {
                    ChoiceRow("Tvar ikon", IconShape.entries, s.iconShape, { it.label }) { v -> edit { it.copy(iconShape = v) } }
                    ClickRow("Balík ikon", IconPacks.list(ctx).firstOrNull { it.pkg == s.iconPack }?.label ?: "Žiadny") { sub = "pack" }
                    SwitchRow(
                        "Tematické ikony", if (Build.VERSION.SDK_INT >= 33) "Jednofarebné ikony podľa farieb systému" else "Vyžaduje Android 13",
                        s.themedIcons, enabled = Build.VERSION.SDK_INT >= 33,
                    ) { v -> edit { it.copy(themedIcons = v) } }
                    SliderRow("Veľkosť ikon v bunke", s.iconScale, 0.4f..1f, 11, { "${(it * 100).roundToInt()} %" }) { v -> edit { it.copy(iconScale = v) } }
                    SwitchRow("Názvy pod ikonami", checked = s.showLabels) { v -> edit { it.copy(showLabels = v) } }
                    SliderRow("Veľkosť názvov", s.labelSize, 8f..18f, 9, { "${it.roundToInt()} sp" }) { v -> edit { it.copy(labelSize = v.roundToInt().toFloat()) } }
                    SwitchRow("Tieň textu", checked = s.labelShadow) { v -> edit { it.copy(labelShadow = v) } }
                }
            }

            item { SectionTitle("Písmo a farby") }
            item {
                Group {
                    ChoiceRow("Písmo", FontChoice.entries, s.font, { it.label }) { v -> edit { it.copy(font = v) } }
                    ChoiceRow("Téma", ThemeMode.entries, s.theme, { it.label }) { v -> edit { it.copy(theme = v) } }
                    SwitchRow("Farby z tapety (Material You)", checked = s.dynamicColor, enabled = Build.VERSION.SDK_INT >= 31) { v ->
                        edit { it.copy(dynamicColor = v) }
                    }
                    if (!s.dynamicColor || Build.VERSION.SDK_INT < 31) {
                        Text("Farba zvýraznenia", Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                        FlowRow(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            ACCENTS.forEach { c ->
                                Box(
                                    Modifier.padding(4.dp).size(40.dp).clip(CircleShape).background(Color(c))
                                        .border(if (c == s.accent) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        .clickable { edit { it.copy(accent = c) } },
                                    contentAlignment = Alignment.Center,
                                ) { if (c == s.accent) Icon(Icons.Filled.Check, null, tint = Color.White) }
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Zoznam aplikácií") }
            item {
                Group {
                    ChoiceRow("Štýl", DrawerStyle.entries, s.drawerStyle, { it.label }) { v -> edit { it.copy(drawerStyle = v) } }
                    ChoiceRow("Zoradiť", DrawerSort.entries, s.drawerSort, { it.label }) { v -> edit { it.copy(drawerSort = v) } }
                    SwitchRow("Naposledy použité hore", checked = s.showRecent) { v -> edit { it.copy(showRecent = v) } }
                    SwitchRow("Aplikácie pracovného profilu", checked = s.showWork) { v -> edit { it.copy(showWork = v) } }
                    SwitchRow("Abecedný posuvník", checked = s.alphabet) { v -> edit { it.copy(alphabet = v) } }
                    SwitchRow("Hneď zobraziť klávesnicu", checked = s.autoKeyboard) { v -> edit { it.copy(autoKeyboard = v) } }
                    SliderRow("Veľkosť ikon", s.drawerIconSize, 28f..64f, 8, { "${it.roundToInt()} dp" }) { v -> edit { it.copy(drawerIconSize = v.roundToInt().toFloat()) } }
                    SliderRow("Nepriehľadnosť pozadia", s.drawerAlpha, 0.3f..1f, 0, { "${(it * 100).roundToInt()} %" }) { v -> edit { it.copy(drawerAlpha = v) } }
                    ClickRow("Skryté aplikácie", "${s.hidden.size}") { sub = "hidden" }
                    ClickRow("Obľúbené", "${s.favorites.size} položiek – poradie, priečinky") { sub = "fav" }
                    SliderRow("Veľkosť ikon v obľúbených", s.favIconSize, 22f..64f, 20, { "${it.roundToInt()} dp" }) { v -> edit { it.copy(favIconSize = v.roundToInt().toFloat()) } }
                    Text("Farba priečinkov v obľúbených", Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                    FlowRow(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        FOLDER_COLORS.forEach { c ->
                            Box(
                                Modifier.padding(4.dp).size(40.dp).clip(CircleShape)
                                    .clickable { edit { it.copy(favFolderColor = c) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Folder, null, tint = Color(c), modifier = Modifier.size(34.dp))
                                if (c == s.favFolderColor) Icon(Icons.Filled.Check, null, tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Notifikácie") }
            item {
                Group {
                    val conn by Badges.connected.collectAsState()
                    val counted by Badges.state.collectAsState()
                    ClickRow(
                        "Stav odznakov",
                        when {
                            !badgesOn -> "Vypnuté – povoľ prístup k notifikáciám (sekcia Povolenia hore)"
                            !conn -> "Povolené, ale služba nie je pripojená – ťukni pre opätovné pripojenie"
                            else -> "Funguje · notifikácie z ${counted.size} aplikácií"
                        },
                    ) {
                        if (!badgesOn) openListenerSettings(act) else {
                            Badges.rebind(act)
                            act.toast("Pripájam službu odznakov…")
                        }
                    }
                    ChoiceRow("Odznaky", BadgeStyle.entries, s.badges, { it.label }) { v -> edit { it.copy(badges = v) } }
                    SliderRow("Veľkosť odznaku", s.badgeSize, 10f..36f, 12, { "${it.roundToInt()} dp" }) { v -> edit { it.copy(badgeSize = v.roundToInt().toFloat()) } }
                    SwitchRow("Text notifikácie v obľúbených", checked = s.favNotifText) { v -> edit { it.copy(favNotifText = v) } }
                }
            }

            item { SectionTitle("Gestá") }
            item {
                Group {
                    ClickRow("Hlasový asistent", "Čo spustí tlačidlo asistenta na ploche a gesto „Hlasový asistent“") { sub = "voice" }
                    GestureRow("Dvojité ťuknutie", s.doubleTap) { v -> edit { it.copy(doubleTap = v) } }
                    GestureRow("Potiahnutie nahor", s.swipeUp) { v -> edit { it.copy(swipeUp = v) } }
                    GestureRow("Potiahnutie nadol", s.swipeDown) { v -> edit { it.copy(swipeDown = v) } }
                }
            }

            item { SectionTitle("Správanie") }
            item {
                Group {
                    SwitchRow("Haptická odozva", "Vibrácia pri podržaní a zmene veľkosti", s.haptics) { v -> edit { it.copy(haptics = v) } }
                    ChoiceRow("Rýchlosť animácií", AnimSpeed.entries, s.animSpeed, { it.label }) { v -> edit { it.copy(animSpeed = v) } }
                    ClickRow("Predvolená domovská aplikácia", if (isDefault) "Fold Launcher je predvolený" else "Fold Launcher nie je predvolený") { act.requestDefaultLauncher() }
                }
            }

            item { SectionTitle("Záloha a údržba") }
            item {
                Group {
                    ClickRow("Exportovať nastavenia a plochu") { act.exportBackup() }
                    ClickRow("Obnoviť zo zálohy") { act.importBackup() }
                    ClickRow("Obnoviť predvolené nastavenia", "Plocha, obľúbené a skryté aplikácie ostanú") { confirmReset = true }
                    ClickRow("Reštartovať launcher") {
                        act.finishAffinity()
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            }

            item {
                Text(
                    "Fold Launcher ${remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }}",
                    Modifier.fillMaxWidth().padding(24.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Obnoviť predvolené nastavenia?") },
        text = { Text("Vzhľad, gestá a zoznam aplikácií sa vrátia na pôvodné hodnoty. Rozloženie plochy ostane.") },
        confirmButton = { TextButton(onClick = { Store.resetSettings(); confirmReset = false }) { Text("Obnoviť") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Zrušiť") } },
    )

    confirmCopy?.let { (from, to) ->
        AlertDialog(
            onDismissRequest = { confirmCopy = null },
            title = { Text("Prepísať rozloženie?") },
            text = { Text("Rozloženie ${if (to == Screen.INNER) "vnútorného" else "vonkajšieho"} displeja sa nahradí kópiou. Widgety sa nekopírujú.") },
            confirmButton = {
                TextButton(onClick = {
                    Store.copyLayout(from, to).forEach { Widgets.delete(it) }
                    confirmCopy = null
                }) { Text("Prepísať") }
            },
            dismissButton = { TextButton(onClick = { confirmCopy = null }) { Text("Zrušiť") } },
        )
    }
}

private fun openListenerSettings(act: android.app.Activity) {
    val cn = sk.fold.launcher.data.BadgeService.component(act).flattenToString()
    val ok = Build.VERSION.SDK_INT >= 30 && runCatching {
        act.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn))
    }.isSuccess
    if (!ok) runCatching { act.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
}

private fun openA11ySettings(act: android.app.Activity) {
    val cn = android.content.ComponentName(act, sk.fold.launcher.data.LockService::class.java).flattenToString()
    val ok = Build.VERSION.SDK_INT >= 31 && runCatching {
        act.startActivity(Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, cn))
    }.isSuccess
    if (!ok) sk.fold.launcher.data.SysActions.openAccessibility(act)
}

@Composable
private fun GestureRow(title: String, value: GestureAction, onSelect: (GestureAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ClickRow(title, value.label) { open = true }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column {
                GestureAction.entries.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(g); open = false }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = g == value, onClick = { onSelect(g); open = false })
                        Text(g.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Zavrieť") } },
    )
}

@Composable
private fun HiddenApps(onClose: () -> Unit) {
    val s by Store.settings.collectAsState()
    val apps by Apps.list.collectAsState()
    val hidden = remember(apps, s.hidden) { apps.filter { it.key in s.hidden } }
    FullSheet("Skryté aplikácie", onClose = onClose) {
        if (hidden.isEmpty()) Text("Žiadne skryté aplikácie. Skryť ich môžeš dlhým podržaním v zozname aplikácií.", Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(hidden, key = { it.key }) { a ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(a, 36.dp, s)
                    Spacer(Modifier.width(14.dp))
                    Text(labelOf(a, s), Modifier.weight(1f))
                    TextButton(onClick = { Store.editSettings { st -> st.copy(hidden = st.hidden - a.key) } }) { Text("Zobraziť") }
                }
            }
        }
    }
}

@Composable
private fun IconPackPicker(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val s by Store.settings.collectAsState()
    val packs = remember { IconPacks.list(ctx) }
    FullSheet("Balík ikon", onClose = onClose) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                PackRow("Žiadny (systémové ikony)", s.iconPack.isEmpty()) { Store.editSettings { it.copy(iconPack = "") } }
            }
            items(packs, key = { it.pkg }) { p ->
                PackRow(p.label, s.iconPack == p.pkg) { Store.editSettings { it.copy(iconPack = p.pkg) } }
            }
            if (packs.isEmpty()) item {
                Text("Nenašiel sa žiadny balík ikon. Nainštaluj ho z Obchodu Play (kompatibilný s Nova/ADW).", Modifier.padding(20.dp))
            }
        }
    }
}

@Composable
private fun PackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}


/** Nastavenia widgetu hodín: štýl, počasie a aplikácie, ktoré sa otvoria po ťuknutí. */
@Composable
fun ClockSettingsPage(ui: LauncherUi, onClose: () -> Unit) {
    val s by Store.settings.collectAsState()
    val w by sk.fold.launcher.data.Weather.state.collectAsState()
    val err by sk.fold.launcher.data.Weather.error.collectAsState()
    val apps by Apps.list.collectAsState()
    val ctx = LocalContext.current
    val act = ui.activity
    val tick = ui.resumeTick
    val locOk = remember(tick) { sk.fold.launcher.data.Weather.hasLocationPermission(ctx) }
    var picking by remember { mutableStateOf<String?>(null) }
    var cityDialog by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler { if (picking != null) picking = null else onClose() }

    picking?.let { which ->
        AppPicker(
            if (which == "clock") "Aplikácia pre čas a dátum" else "Aplikácia pre počasie",
            listOfNotNull((if (which == "clock") s.clockApp else s.weatherApp).ifEmpty { null }),
            onClose = { picking = null }, single = true,
            extras = remember(which) { Apps.hiddenWeatherApps(ctx) },
        ) { keys ->
            val k = keys.firstOrNull() ?: ""
            edit { if (which == "clock") it.copy(clockApp = k) else it.copy(weatherApp = k) }
            picking = null
        }
        return
    }
    fun appName(k: String) = apps.firstOrNull { it.key == k }?.let { labelOf(it, s) } ?: Apps.labelForKey(ctx, k)

    FullSheet("Hodiny a počasie", onClose = onClose) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionTitle("Hodiny") }
            item {
                Group {
                    ChoiceRow("Štýl", ClockStyle.entries, s.clockStyle, { it.label }) { v -> edit { it.copy(clockStyle = v) } }
                    SwitchRow("24-hodinový čas", checked = s.clock24) { v -> edit { it.copy(clock24 = v) } }
                    SwitchRow("Dátum", checked = s.clockDate) { v -> edit { it.copy(clockDate = v) } }
                    SwitchRow("Udalosť z kalendára pod dátumom", "Ťuknutím sa otvorí prehľad nasledujúcich udalostí", s.showEvents) { v ->
                        edit { it.copy(showEvents = v) }
                        if (v && !sk.fold.launcher.data.Agenda.hasPermission(ctx)) act.requestCalendar()
                    }
                    ClickRow("Ťuknutie na čas a dátum otvorí", appName(s.clockApp) ?: "Budíky (predvolené)") { picking = "clock" }
                }
            }
            item { SectionTitle("Počasie") }
            item {
                Group {
                    SwitchRow("Zobraziť počasie", "Aktuálna teplota z Open-Meteo", s.showWeather) { v -> edit { it.copy(showWeather = v) } }
                    ClickRow(
                        "Miesto",
                        if (s.weatherCity.isNotBlank()) s.weatherCity
                        else if (locOk) "Poloha telefónu" else "Poloha telefónu – nie je povolená (ťukni)",
                    ) { if (s.weatherCity.isBlank() && !locOk) act.requestLocation() else cityDialog = true }
                    if (s.weatherCity.isBlank() && locOk) ClickRow("Zadať mesto ručne") { cityDialog = true }
                    SwitchRow("Stupne Fahrenheita", checked = s.weatherF) { v -> edit { it.copy(weatherF = v) } }
                    ClickRow("Ťuknutie na počasie otvorí", appName(s.weatherApp) ?: "Predpoveď na webe (predvolené)") { picking = "weather" }
                    ClickRow(
                        "Stav",
                        err ?: w?.let { "${it.temp}°" + (if (it.place.isNotBlank()) " · ${it.place}" else "") + " · ťukni pre obnovenie" }
                            ?: "Načítavam… (ťukni pre obnovenie)",
                    ) { scope.launch { sk.fold.launcher.data.Weather.refresh(ctx, force = true) } }
                }
            }
        }
    }
    if (cityDialog) TextDialog("Mesto pre počasie", s.weatherCity, "Prázdne = poloha telefónu", onDismiss = { cityDialog = false }) { v ->
        edit { it.copy(weatherCity = v) }
        cityDialog = false
        if (v.isBlank() && !sk.fold.launcher.data.Weather.hasLocationPermission(ctx)) act.requestLocation()
    }
}


/** Výber hlasového asistenta pre tlačidlo na ploche. */
@Composable
fun AssistantPage(ui: LauncherUi, onClose: () -> Unit) {
    val s by Store.settings.collectAsState()
    val apps by Apps.list.collectAsState()
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val targets = remember { Apps.voiceTargets(ctx) }
    BackHandler { if (picking) picking = false else onClose() }
    if (picking) {
        AppPicker("Aplikácia pre tlačidlo asistenta", listOfNotNull(s.assistantKey.ifEmpty { null }),
            onClose = { picking = false }, single = true) { keys ->
            edit { it.copy(assistantKey = keys.firstOrNull() ?: "") }
            picking = false
        }
        return
    }
    val customApp = apps.firstOrNull { it.key == s.assistantKey }
    FullSheet("Hlasový asistent", onClose = onClose) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "Ťukni na ▶ pri každej možnosti a vyskúšaj, ktorá hneď začne počúvať. Tú potom vyber. Vyber, čo spustí tlačidlo asistenta. Hlasovým príkazom pre Gemini alebo Google Asistenta ovládaš telefón aj " +
                        "zariadenia Google Home. Predvolený asistent je ten, ktorý máš v Nastaveniach → Aplikácie → Predvolené aplikácie → Digitálny asistent.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            item {
                Group {
                    PackRow("Predvolený asistent systému", s.assistantKey.isEmpty()) { edit { it.copy(assistantKey = "") } }
                }
            }
            if (targets.isNotEmpty()) {
                item { SectionTitle("Hlasové príkazy a asistenti") }
                item {
                    Group {
                        targets.forEach { tg ->
                            Row(
                                Modifier.fillMaxWidth().clickable { edit { it.copy(assistantKey = tg.key) } }.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = s.assistantKey == tg.key, onClick = { edit { it.copy(assistantKey = tg.key) } })
                                val img = remember(tg.key) { tg.icon?.let { sk.fold.launcher.data.IconCache.toBitmap(it, 96).asImageBitmap() } }
                                if (img != null) androidx.compose.foundation.Image(img, null, Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(tg.label, Modifier.weight(1f))
                                IconButton(onClick = { sk.fold.launcher.data.Apps.launchKey(ctx, tg.key) }) {
                                    Icon(Icons.Filled.PlayArrow, "Vyskúšať")
                                }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Iné") }
            item {
                Group {
                    ClickRow("Iná aplikácia…", customApp?.let { "Vybraté: " + labelOf(it, s) } ?: "Napr. aplikácia Gemini alebo Bixby") { picking = true }
                    ClickRow("Vyskúšať", "Spustí aktuálne zvoleného asistenta") { ui.openAssistant() }
                }
            }
        }
    }
}
