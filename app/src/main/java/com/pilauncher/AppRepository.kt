package com.pilauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
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
                val icon = info.loadIcon(pm)
                found[pkg] = AppEntry(
                    packageName = pkg,
                    activityName = activity.name,
                    label = info.loadLabel(pm).toString(),
                    banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm),
                    icon = icon,
                    isLeanback = category == Intent.CATEGORY_LEANBACK_LAUNCHER,
                    tileColor = tileColorFor(icon),
                )
            }
        }

        val hidden = if (includeHidden) emptySet() else hiddenPackages()
        val rank = savedOrder().withIndex().associate { it.value to it.index }
        return found.values
            .filter { it.packageName !in hidden }
            .sortedWith(compareBy<AppEntry>({ rank[it.packageName] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
    }

    /** Average colour of the icon, kept fairly dark so the icon stands out on it. */
    private fun tileColorFor(icon: Drawable): Int {
        val size = 24
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val copy = icon.constantState?.newDrawable()?.mutate() ?: icon
        copy.setBounds(0, 0, size, size)
        copy.draw(Canvas(bitmap))

        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (x in 0 until size) for (y in 0 until size) {
            val c = bitmap.getPixel(x, y)
            if (Color.alpha(c) < 200) continue
            r += Color.red(c); g += Color.green(c); b += Color.blue(c)
            count++
        }
        bitmap.recycle()
        if (count == 0) return DEFAULT_TILE_COLOR

        val hsv = FloatArray(3)
        Color.RGBToHSV((r / count).toInt(), (g / count).toInt(), (b / count).toInt(), hsv)
        hsv[2] = hsv[2].coerceIn(0.22f, 0.5f)
        return Color.HSVToColor(hsv)
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

    /** Show every app as an icon tile, even TV apps that have a banner. */
    var iconsOnly: Boolean
        get() = prefs.getBoolean(KEY_ICONS_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_ICONS_ONLY, value).apply()

    /** The chosen photo is copied here, resized for the TV. */
    fun wallpaperFile() = File(context.filesDir, "wallpaper.jpg")

    companion object {
        private const val KEY_ORDER = "order"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_WALLPAPER = "wallpaper"
        private const val KEY_USE_PHOTO = "use_photo"
        private const val KEY_BACKDROP = "app_backdrop"
        private const val KEY_ICONS_ONLY = "icons_only"
        private const val DEFAULT_TILE_COLOR = 0xFF2C2C2E.toInt()
    }
}
