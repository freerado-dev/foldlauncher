package sk.fold.launcher.data

enum class IconShape(val label: String) {
    SYSTEM("Systém"), CIRCLE("Kruh"), SQUIRCLE("Squircle"), ROUNDED("Zaoblený"),
    SQUARE("Štvorec"), TEARDROP("Kvapka"),
}

enum class FontChoice(val label: String) {
    SYSTEM("Systémové"), SERIF("Pätkové"), MONO("Strojové"), CONDENSED("Zúžené"), LIGHT("Tenké"),
}

enum class ThemeMode(val label: String) { AUTO("Podľa systému"), LIGHT("Svetlá"), DARK("Tmavá") }

enum class GestureAction(val label: String) {
    NONE("Nič"), DRAWER("Zoznam aplikácií"), SEARCH("Vyhľadávanie"), NOTIFICATIONS("Notifikácie"),
    QUICK_SETTINGS("Rýchle nastavenia"), LOCK("Zamknúť obrazovku"), EDIT("Upraviť plochu"),
    SETTINGS("Nastavenia launchera"), VOICE("Hlasový asistent"),
}

enum class BadgeStyle(val label: String) { NONE("Vypnuté"), DOT("Bodka"), COUNT("Počet") }

enum class DrawerStyle(val label: String) { LIST("Zoznam"), GRID("Mriežka") }

enum class ClockStyle(val label: String) { LARGE("Veľké"), STACKED("Pod sebou"), MINIMAL("Malé") }

/** Vzhľad celého launchera. */
enum class LauncherTheme(val label: String) { STANDARD("Štandard"), HOLO("Hologram") }

enum class PageEffect(val label: String) { SLIDE("Posun"), FADE("Prelínanie"), ZOOM("Zoom"), CUBE("Kocka") }

enum class AnimSpeed(val label: String, val stiffness: Float) {
    SLOW("Pomalé", 120f), NORMAL("Normálne", 300f), FAST("Rýchle", 700f),
}

enum class DrawerSort(val label: String) { ALPHA("Abecedne"), USAGE("Najpoužívanejšie"), INSTALL("Najnovšie") }

/** Priečinok v zozname obľúbených (kľúč v obľúbených má tvar "folder:<id>"). */
data class FavFolder(val name: String, val apps: List<String>)

const val FOLDER_PREFIX = "folder:"

val FOLDER_COLORS: List<Long> = listOf(
    0xFF5B8DEF, 0xFF34C759, 0xFFFF9F0A, 0xFFFF453A, 0xFFBF5AF2,
    0xFF64D2FF, 0xFFFFD60A, 0xFFAC8E68, 0xFF8E8E93, 0xFFFFFFFF,
)

val ACCENTS: List<Long> = listOf(
    0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFFF375F,
    0xFFBF5AF2, 0xFF64D2FF, 0xFFFFD60A, 0xFFAC8E68,
)

data class Settings(
    val theme: ThemeMode = ThemeMode.AUTO,
    val dynamicColor: Boolean = true,
    val accent: Long = ACCENTS[0],
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconPack: String = "",
    val themedIcons: Boolean = false,
    val iconScale: Float = 0.8f,
    val showLabels: Boolean = true,
    val labelSize: Float = 12f,
    val labelShadow: Boolean = true,
    val font: FontChoice = FontChoice.SYSTEM,
    val hideStatusBar: Boolean = false,
    val wallpaperDim: Float = 0f,
    val showGrid: Boolean = true,
    val lockLayout: Boolean = false,
    val drawerStyle: DrawerStyle = DrawerStyle.LIST,
    val drawerAlpha: Float = 0.94f,
    val alphabet: Boolean = true,
    val autoKeyboard: Boolean = false,
    val drawerIconSize: Float = 40f,
    val badges: BadgeStyle = BadgeStyle.DOT,
    val favNotifText: Boolean = true,
    val doubleTap: GestureAction = GestureAction.LOCK,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val swipeUp: GestureAction = GestureAction.DRAWER,
    val clock24: Boolean = true,
    val clockDate: Boolean = true,
    val clockStyle: ClockStyle = ClockStyle.LARGE,
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val initialized: Boolean = false,
    val pageEffect: PageEffect = PageEffect.SLIDE,
    val showPageDots: Boolean = true,
    val autoAddApps: Boolean = false,
    val lockPortrait: Boolean = false,
    val hideNavBar: Boolean = false,
    val haptics: Boolean = true,
    val animSpeed: AnimSpeed = AnimSpeed.NORMAL,
    val drawerSort: DrawerSort = DrawerSort.ALPHA,
    val showRecent: Boolean = true,
    val showWork: Boolean = true,
    val favFolders: Map<String, FavFolder> = emptyMap(),
    val favIconSize: Float = 30f,
    val favFolderColor: Long = 0xFF5B8DEF,
    /** Aplikácia po ťuknutí na čas/dátum ("" = budíky). */
    val clockApp: String = "",
    /** Aplikácia po ťuknutí na počasie ("" = predpoveď na webe). */
    val weatherApp: String = "",
    val showWeather: Boolean = true,
    val showEvents: Boolean = true,
    /** true = priečinky sa otvárajú ako panel zdola (ako udalosti), false = okno. */
    val folderSheet: Boolean = false,
    /** Feed stránka naľavo od hlavnej plochy. */
    val feedEnabled: Boolean = true,
    val launcherTheme: LauncherTheme = LauncherTheme.STANDARD,
    /** Farba pozadia okien a panelov (priečinky, Váš program) – RGB a priehľadnosť. */
    val popupColor: Long = 0xFF1C1C20,
    val popupAlpha: Float = 0.95f,
    /** Mesto pre počasie ("" = poloha telefónu). */
    val weatherCity: String = "",
    val weatherF: Boolean = false,
    val badgeSize: Float = 22f,
    /** Čo spustí tlačidlo asistenta ("" = predvolený asistent systému). */
    val assistantKey: String = "",
    /** Plávajúce tlačidlo asistenta mimo mriežky (pozícia ako podiel plochy 0..1, zvlášť pre každý displej). */
    val floatAssist: Boolean = false,
    val floatSize: Float = 56f,
    val floatCoverX: Float = 0.85f, val floatCoverY: Float = 0.8f,
    val floatInnerX: Float = 0.9f, val floatInnerY: Float = 0.8f,
    /** false = len na jednej stránke plochy (floatCoverPage / floatInnerPage). */
    val floatAllPages: Boolean = false,
    val floatCoverPage: Int = 0, val floatInnerPage: Int = 0,
    /** Plávajúci Holo kruh mimo mriežky. */
    val floatRing: Boolean = false,
    val floatRingSize: Float = 160f,
    val floatRingCoverX: Float = 0.5f, val floatRingCoverY: Float = 0.35f,
    val floatRingInnerX: Float = 0.5f, val floatRingInnerY: Float = 0.35f,
    val floatRingAllPages: Boolean = false,
    val floatRingCoverPage: Int = 0, val floatRingInnerPage: Int = 0,
)
