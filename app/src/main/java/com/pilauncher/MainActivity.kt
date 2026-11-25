package com.pilauncher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var repo: AppRepository
    private lateinit var adapter: AppAdapter
    private lateinit var grid: RecyclerView
    private lateinit var backdrop: ImageView
    private lateinit var shelfTitle: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = DateFormat.getTimeFormat(this@MainActivity).format(Date())
            date.text = DateFormat.format("EEEE, d MMMM", Date())
            handler.postDelayed(this, 15_000)
        }
    }

    // Name to gradient colours. Add your own here.
    private val wallpapers = listOf(
        "Midnight" to intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt()),
        "Graphite" to intArrayOf(0xFF2C2C2E.toInt(), 0xFF1C1C1E.toInt(), 0xFF000000.toInt()),
        "Aurora" to intArrayOf(0xFF0B3D2E.toInt(), 0xFF145A4A.toInt(), 0xFF1F1147.toInt()),
        "Sunset" to intArrayOf(0xFF2B1055.toInt(), 0xFF7A2A5B.toInt(), 0xFFB24A3B.toInt()),
        "Ocean" to intArrayOf(0xFF000428.toInt(), 0xFF004E92.toInt(), 0xFF00203F.toInt()),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = AppRepository(this)
        grid = findViewById(R.id.grid)
        backdrop = findViewById(R.id.backdrop)
        shelfTitle = findViewById(R.id.shelfTitle)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)

        adapter = AppAdapter(
            onClick = { pos -> launch(adapter.items[pos]) },
            onLongClick = { pos -> showAppOptions(pos) },
            onFocused = { app -> showOnShelf(app) },
        )
        grid.layoutManager = GridLayoutManager(this, COLUMNS)
        grid.adapter = adapter

        findViewById<View>(R.id.settingsButton).setOnClickListener { showSettings() }

        // Back does nothing on a home screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        applyWallpaper()
    }

    override fun onResume() {
        super.onResume()
        reloadApps()
        handler.post(clockTick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTick)
        if (adapter.movingPackage != null) finishMove()
    }

    private fun reloadApps() {
        adapter.submit(repo.loadApps())
        grid.post {
            if (currentFocus == null || currentFocus == grid) focusPosition(0)
        }
    }

    private fun focusPosition(pos: Int) {
        if (pos !in adapter.items.indices) return
        grid.scrollToPosition(pos)
        grid.post {
            grid.findViewHolderForAdapterPosition(pos)
                ?.itemView?.findViewById<View>(R.id.tile)?.requestFocus()
        }
    }

    private fun showOnShelf(app: AppEntry) {
        shelfTitle.text = app.label
        backdrop.setImageDrawable(app.banner ?: app.icon)
    }

    private fun launch(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(app.packageName, app.activityName)
            addCategory(
                if (app.isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cant_open, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- App options (hold OK on an app) ----------

    private fun showAppOptions(pos: Int) {
        val app = adapter.items[pos]
        val options = arrayOf(
            getString(R.string.move),
            getString(R.string.hide),
            getString(R.string.app_info),
            getString(R.string.uninstall),
        )
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startMove(pos)
                    1 -> {
                        repo.saveOrder(adapter.items.map { it.packageName })
                        repo.hide(app.packageName)
                        reloadApps()
                    }
                    2 -> startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${app.packageName}"))
                    )
                    3 -> startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                    )
                }
            }
            .show()
    }

    // ---------- Move mode ----------

    private fun startMove(pos: Int) {
        adapter.setMoving(adapter.items[pos].packageName)
        Toast.makeText(this, R.string.move_hint, Toast.LENGTH_LONG).show()
    }

    private fun finishMove() {
        adapter.setMoving(null)
        repo.saveOrder(adapter.items.map { it.packageName })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val moving = adapter.movingPackage ?: return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
            val from = adapter.items.indexOfFirst { it.packageName == moving }
            val to = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> from - 1
                KeyEvent.KEYCODE_DPAD_RIGHT -> from + 1
                KeyEvent.KEYCODE_DPAD_UP -> from - COLUMNS
                KeyEvent.KEYCODE_DPAD_DOWN -> from + COLUMNS
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    finishMove()
                    return true
                }
                else -> return super.dispatchKeyEvent(event)
            }
            if (from >= 0 && to in adapter.items.indices) {
                adapter.move(from, to)
                focusPosition(to)
            }
        }
        return true
    }

    // ---------- Launcher settings (gear button) ----------

    private fun showSettings() {
        val options = arrayOf(
            getString(R.string.wallpaper),
            getString(R.string.hidden_apps),
            getString(R.string.reset_order),
            getString(R.string.android_settings),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> chooseWallpaper()
                    1 -> showHiddenApps()
                    2 -> {
                        repo.saveOrder(emptyList())
                        reloadApps()
                    }
                    3 -> startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .show()
    }

    private fun chooseWallpaper() {
        val names = wallpapers.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.wallpaper)
            .setSingleChoiceItems(names, repo.wallpaperIndex) { dialog, which ->
                repo.wallpaperIndex = which
                applyWallpaper()
                dialog.dismiss()
            }
            .show()
    }

    private fun applyWallpaper() {
        val colors = wallpapers.getOrElse(repo.wallpaperIndex) { wallpapers[0] }.second
        findViewById<View>(R.id.root).background =
            GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
    }

    private fun showHiddenApps() {
        val hidden = repo.hiddenPackages().toList()
        if (hidden.isEmpty()) {
            Toast.makeText(this, R.string.no_hidden_apps, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = hidden.map { pkg ->
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.tap_to_unhide)
            .setItems(labels) { _, which ->
                repo.unhide(hidden[which])
                reloadApps()
            }
            .show()
    }

    companion object {
        const val COLUMNS = 5
    }
}
