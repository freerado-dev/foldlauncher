package sk.fold.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.Normalizer
import java.util.concurrent.Executors

class AppInfo(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val serial: Long,
    val work: Boolean,
    val info: LauncherActivityInfo,
    val installed: Long = 0,
) {
    val pkg: String get() = component.packageName
}

fun labelOf(a: AppInfo, s: Settings): String = s.renames[a.key]?.takeIf { it.isNotBlank() } ?: a.label

private val MARKS = Regex("\\p{Mn}+")
fun norm(t: String): String = MARKS.replace(Normalizer.normalize(t, Normalizer.Form.NFD), "").lowercase()

fun letterOf(label: String): String {
    val c = norm(label).firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c.isLetter()) c.toString() else "#"
}

object Apps {
    lateinit var la: LauncherApps
    lateinit var um: UserManager
    private lateinit var ctx: Context
    val list = MutableStateFlow<List<AppInfo>>(emptyList())
    private val exec = Executors.newSingleThreadExecutor()

    fun key(c: ComponentName, serial: Long) = "${c.flattenToString()}|$serial"

    fun init(context: Context) {
        ctx = context.applicationContext
        la = ctx.getSystemService(LauncherApps::class.java)
        um = ctx.getSystemService(UserManager::class.java)
        la.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                reload()
                if (packageName != null && user == Process.myUserHandle()) Store.removePackage(packageName)
            }
            override fun onPackageAdded(packageName: String?, user: UserHandle?) {
                reload {
                    // Nová aplikácia sa môže automaticky pridať na plochu oboch displejov.
                    if (Store.settings.value.autoAddApps && packageName != null) {
                        list.value.filter { it.pkg == packageName && it.user == user }.forEach { a ->
                            for (s in Screen.entries) Store.addApp(s, a.key, 0)
                        }
                    }
                }
            }
            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                IconCache.clear(); reload()
            }
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        }, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload(after: (() -> Unit)? = null) = exec.execute {
        val out = ArrayList<AppInfo>()
        val me = Process.myUserHandle()
        val pm = ctx.packageManager
        val installTimes = HashMap<String, Long>()
        for (user in um.userProfiles) {
            val serial = um.getSerialNumberForUser(user)
            val acts = runCatching { la.getActivityList(null, user) }.getOrNull() ?: continue
            for (i in acts) {
                if (i.componentName.packageName == ctx.packageName) continue
                val pkg = i.componentName.packageName
                val inst = installTimes.getOrPut(pkg) {
                    runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L)
                }
                out += AppInfo(key(i.componentName, serial), i.label?.toString() ?: pkg,
                    i.componentName, user, serial, user != me, i, inst)
            }
        }
        list.value = out
        if (!Store.settings.value.initialized) Store.ensureDefaults(defaultFavorites())
        runCatching { after?.invoke() }
    }

    fun find(key: String?): AppInfo? = key?.let { k -> list.value.firstOrNull { it.key == k } }

    fun launch(c: Context, a: AppInfo, bounds: Rect? = null) {
        runCatching { la.startMainActivity(a.component, a.user, bounds, null); Store.recordLaunch(a.key) }
            .onFailure { Toast.makeText(c, "Aplikáciu sa nepodarilo spustiť", Toast.LENGTH_SHORT).show() }
    }

    fun canShortcuts(): Boolean = runCatching { la.hasShortcutHostPermission() }.getOrDefault(false)

    fun shortcuts(a: AppInfo): List<ShortcutInfo> = runCatching {
        if (!la.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery().setPackage(a.pkg).setActivity(a.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        (la.getShortcuts(q, a.user) ?: emptyList()).filter { it.isEnabled }.sortedBy { it.rank }.take(6)
    }.getOrDefault(emptyList())

    fun shortcutInfo(pkg: String, id: String, user: UserHandle): ShortcutInfo? = runCatching {
        val q = LauncherApps.ShortcutQuery().setPackage(pkg).setShortcutIds(listOf(id))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        la.getShortcuts(q, user)?.firstOrNull()
    }.getOrNull()

    fun startShortcut(c: Context, pkg: String, id: String, user: UserHandle) {
        runCatching { la.startShortcut(pkg, id, null, null, user) }
            .onFailure { Toast.makeText(c, "Skratka nie je dostupná", Toast.LENGTH_SHORT).show() }
    }

    fun pinShortcut(s: ShortcutInfo): Boolean = runCatching {
        val q = LauncherApps.ShortcutQuery().setPackage(s.`package`)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val pinned = la.getShortcuts(q, s.userHandle)?.map { it.id }.orEmpty()
        la.pinShortcuts(s.`package`, (pinned + s.id).distinct(), s.userHandle)
        true
    }.getOrDefault(false)

    fun userFor(serial: Long): UserHandle? = runCatching { um.getUserForSerialNumber(serial) }.getOrNull()
    fun serialOf(u: UserHandle): Long = um.getSerialNumberForUser(u)

    fun details(c: Context, a: AppInfo) {
        runCatching { la.startAppDetailsActivity(a.component, a.user, null, null) }
            .onFailure { Toast.makeText(c, "Nedá sa otvoriť", Toast.LENGTH_SHORT).show() }
    }

    fun uninstall(c: Context, a: AppInfo) {
        runCatching {
            c.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun defaultFavorites(): List<String> {
        val pm = ctx.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        val apps = list.value
        return intents.mapNotNull { i ->
            val pkg = runCatching { pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }.getOrNull()
            apps.firstOrNull { it.pkg == pkg && !it.work }?.key
        }.distinct()
    }
}
