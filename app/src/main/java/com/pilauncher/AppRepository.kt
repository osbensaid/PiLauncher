package com.pilauncher

import android.content.Context
import android.content.Intent
import java.io.File

/** Finds installed apps and remembers the order, hidden apps and background. */
class AppRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("pilauncher", Context.MODE_PRIVATE)

    fun loadApps(includeHidden: Boolean = false): List<AppEntry> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, AppEntry>()

        // TV apps first so they win over the phone version of the same app
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (category in categories) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, 0)) {
                val activity = info.activityInfo
                val pkg = activity.packageName
                if (pkg == context.packageName || found.containsKey(pkg)) continue
                found[pkg] = AppEntry(
                    packageName = pkg,
                    activityName = activity.name,
                    label = info.loadLabel(pm).toString(),
                    banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm),
                    icon = info.loadIcon(pm),
                    isLeanback = category == Intent.CATEGORY_LEANBACK_LAUNCHER,
                )
            }
        }

        val hidden = if (includeHidden) emptySet() else hiddenPackages()
        val rank = savedOrder().withIndex().associate { it.value to it.index }
        return found.values
            .filter { it.packageName !in hidden }
            .sortedWith(compareBy<AppEntry>({ rank[it.packageName] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
    }

    private fun savedOrder(): List<String> =
        prefs.getString(KEY_ORDER, "").orEmpty().split(",").filter { it.isNotEmpty() }

    fun saveOrder(packages: List<String>) {
        prefs.edit().putString(KEY_ORDER, packages.joinToString(",")).apply()
    }

    fun hiddenPackages(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun setHidden(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN, packages).apply()
    }

    fun hide(pkg: String) = setHidden(hiddenPackages() + pkg)

    var wallpaperIndex: Int
        get() = prefs.getInt(KEY_WALLPAPER, 0)
        set(value) = prefs.edit().putInt(KEY_WALLPAPER, value).apply()

    /** True when the background photo is used instead of a colour gradient. */
    var usePhoto: Boolean
        get() = prefs.getBoolean(KEY_USE_PHOTO, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_PHOTO, value).apply()

    /** Show the selected app's picture faded behind the tiles. */
    var showAppBackdrop: Boolean
        get() = prefs.getBoolean(KEY_BACKDROP, true)
        set(value) = prefs.edit().putBoolean(KEY_BACKDROP, value).apply()

    /** The chosen photo is copied here, resized for the TV. */
    fun wallpaperFile() = File(context.filesDir, "wallpaper.jpg")

    companion object {
        private const val KEY_ORDER = "order"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_WALLPAPER = "wallpaper"
        private const val KEY_USE_PHOTO = "use_photo"
        private const val KEY_BACKDROP = "app_backdrop"
    }
}
