package com.pilauncher

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var repo: AppRepository
    private lateinit var adapter: AppAdapter
    private lateinit var grid: RecyclerView
    private lateinit var wallpaperImage: ImageView
    private lateinit var backdrop: ImageView
    private lateinit var shelfTitle: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView

    private val background = Executors.newSingleThreadExecutor()
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

    private val photoPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openPhotoPicker()
            else Toast.makeText(this, R.string.photo_permission_needed, Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = AppRepository(this)
        grid = findViewById(R.id.grid)
        wallpaperImage = findViewById(R.id.wallpaperImage)
        backdrop = findViewById(R.id.backdrop)
        shelfTitle = findViewById(R.id.shelfTitle)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)

        adapter = AppAdapter(
            onClick = { pos -> launch(adapter.items[pos]) },
            onLongClick = { pos -> showAppOptions(pos) },
            onFocused = { app -> showOnShelf(app) },
        )
        adapter.iconsOnly = repo.iconsOnly
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
        backdrop.setImageDrawable(if (repo.showAppBackdrop) app.banner ?: app.icon else null)
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
        val backdropLabel = getString(
            if (repo.showAppBackdrop) R.string.app_backdrop_on else R.string.app_backdrop_off
        )
        val tileStyleLabel = getString(
            if (repo.iconsOnly) R.string.tiles_icons_only else R.string.tiles_mixed
        )
        val options = arrayOf(
            getString(R.string.choose_apps),
            getString(R.string.background),
            backdropLabel,
            tileStyleLabel,
            getString(R.string.reset_order),
            getString(R.string.android_settings),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> chooseVisibleApps()
                    1 -> chooseBackground()
                    2 -> {
                        repo.showAppBackdrop = !repo.showAppBackdrop
                        if (!repo.showAppBackdrop) backdrop.setImageDrawable(null)
                    }
                    3 -> {
                        repo.iconsOnly = !repo.iconsOnly
                        adapter.iconsOnly = repo.iconsOnly
                    }
                    4 -> {
                        repo.saveOrder(emptyList())
                        reloadApps()
                    }
                    5 -> startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .show()
    }

    /** Checklist of every app: ticked apps are shown on the home screen. */
    private fun chooseVisibleApps() {
        val all = repo.loadApps(includeHidden = true).sortedBy { it.label.lowercase() }
        val hidden = repo.hiddenPackages()
        val labels = all.map { it.label }.toTypedArray()
        val checked = BooleanArray(all.size) { all[it].packageName !in hidden }

        AlertDialog.Builder(this)
            .setTitle(R.string.choose_apps)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.save) { _, _ ->
                val newHidden = all.filterIndexed { i, _ -> !checked[i] }
                    .map { it.packageName }.toSet()
                repo.setHidden(newHidden)
                reloadApps()
            }
            .setNeutralButton(R.string.show_all) { _, _ ->
                repo.setHidden(emptySet())
                reloadApps()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- Background ----------

    private fun chooseBackground() {
        val options = arrayOf(
            getString(R.string.choose_photo),
            getString(R.string.colour_gradient),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.background)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> choosePhoto()
                    1 -> chooseGradient()
                }
            }
            .show()
    }

    private fun chooseGradient() {
        val names = wallpapers.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.colour_gradient)
            .setSingleChoiceItems(names, repo.wallpaperIndex) { dialog, which ->
                repo.wallpaperIndex = which
                repo.usePhoto = false
                applyWallpaper()
                dialog.dismiss()
            }
            .show()
    }

    private fun choosePhoto() {
        if (ContextCompat.checkSelfPermission(this, photoPermission) == PackageManager.PERMISSION_GRANTED) {
            openPhotoPicker()
        } else {
            permissionLauncher.launch(photoPermission)
        }
    }

    private fun openPhotoPicker() {
        background.execute {
            val photos = queryPhotos()
            runOnUiThread {
                if (photos.isEmpty()) {
                    Toast.makeText(this, R.string.no_photos, Toast.LENGTH_LONG).show()
                } else {
                    showPhotoGrid(photos)
                }
            }
        }
    }

    /** Newest photos first, from the Pi's storage and any USB stick. */
    private fun queryPhotos(): List<Uri> {
        val photos = mutableListOf<Uri>()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && photos.size < 300) {
                photos += ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
            }
        }
        return photos
    }

    private fun showPhotoGrid(photos: List<Uri>) {
        val list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            setPadding(24, 24, 24, 24)
            clipToPadding = false
        }
        var dialog: AlertDialog? = null
        val photoAdapter = PhotoAdapter(photos) { uri ->
            dialog?.dismiss()
            setPhotoWallpaper(uri)
        }
        list.adapter = photoAdapter

        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.choose_photo)
            .setView(list)
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { photoAdapter.shutdown() }
            .show()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.8).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    /** Copies the photo, resized for the TV, so it loads fast and survives the original being deleted. */
    private fun setPhotoWallpaper(uri: Uri) {
        Toast.makeText(this, R.string.setting_background, Toast.LENGTH_SHORT).show()
        background.execute {
            val ok = try {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                    var sample = 1
                    while (info.size.width / (sample * 2) >= 1920 && info.size.height / (sample * 2) >= 1080) {
                        sample *= 2
                    }
                    decoder.setTargetSampleSize(sample)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                repo.wallpaperFile().outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                }
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (ok) {
                    repo.usePhoto = true
                    applyWallpaper()
                } else {
                    Toast.makeText(this, R.string.photo_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyWallpaper() {
        val colors = wallpapers.getOrElse(repo.wallpaperIndex) { wallpapers[0] }.second
        findViewById<View>(R.id.root).background =
            GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)

        val file = repo.wallpaperFile()
        if (repo.usePhoto && file.exists()) {
            wallpaperImage.setImageBitmap(BitmapFactory.decodeFile(file.path))
            wallpaperImage.visibility = View.VISIBLE
        } else {
            wallpaperImage.setImageDrawable(null)
            wallpaperImage.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        background.shutdown()
    }

    companion object {
        const val COLUMNS = 5
    }
}
