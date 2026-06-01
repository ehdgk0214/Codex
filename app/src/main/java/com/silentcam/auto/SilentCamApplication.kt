package com.silentcam.auto

import android.app.Application
import com.silentcam.auto.notifications.NotificationHelper

class SilentCamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
