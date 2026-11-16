package com.example.geckobrowser

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class BrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}
