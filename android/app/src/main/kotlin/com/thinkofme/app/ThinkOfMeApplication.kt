package com.thinkofme.app

import android.app.Application
import com.thinkofme.app.push.createNotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ThinkOfMeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
        // App Check: proves requests come from this app (debug builds use a debug token).
        if (com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()) installAppCheck()
    }
}
