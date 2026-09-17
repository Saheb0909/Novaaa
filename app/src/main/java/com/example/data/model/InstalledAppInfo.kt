package com.example.data.model

import android.graphics.drawable.Drawable

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean = false
)
