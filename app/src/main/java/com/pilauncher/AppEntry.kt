package com.pilauncher

import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val banner: Drawable?,   // TV apps provide a wide 16:9 banner
    val icon: Drawable,      // phone apps only have a square icon
    val isLeanback: Boolean,
)
