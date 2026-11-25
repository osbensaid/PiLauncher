package com.pilauncher

import android.content.Context
import android.content.Intent

/** Finds installed apps and remembers the order, hidden apps and wallpaper. */
class AppRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("pilauncher", Context.MODE_PRIVATE)

    fun loadApps(): List<AppEntry> {
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

        val hidden = hiddenPackages()
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

    fun hide(pkg: String) {
        prefs.edit().putStringSet(KEY_HIDDEN, hiddenPackages() + pkg).apply()
    }

    fun unhide(pkg: String) {
        prefs.edit().putStringSet(KEY_HIDDEN, hiddenPackages() - pkg).apply()
    }

    var wallpaperIndex: Int
        get() = prefs.getInt(KEY_WALLPAPER, 0)
        set(value) = prefs.edit().putInt(KEY_WALLPAPER, value).apply()

    companion object {
        private const val KEY_ORDER = "order"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_WALLPAPER = "wallpaper"
    }
}
