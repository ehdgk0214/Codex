package com.silentcam.auto.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val request = OneTimeWorkRequestBuilder<ApplySilentCameraWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork("boot-reapply-silent-camera", ExistingWorkPolicy.REPLACE, request)
    }
}
