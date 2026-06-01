package com.silentcam.auto.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.silentcam.auto.CameraSetting
import com.silentcam.auto.SettingsRepository
import com.silentcam.auto.notifications.NotificationHelper
import com.silentcam.auto.shizuku.ShizukuCommandExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 재부팅/앱 업데이트 후 WorkManager가 실행하는 재적용 작업입니다.
 * Shizuku 서비스가 아직 올라오지 않은 경우가 많아 짧게 재시도합니다.
 */
class ApplySilentCameraWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!SettingsRepository(applicationContext).autoApplyAfterBoot.first()) return Result.success()
        val shizuku = ShizukuCommandExecutor(applicationContext)
        repeat(3) { attempt ->
            if (shizuku.hasPermission()) {
                val result = shizuku.execute(CameraSetting.DISABLE_FORCED_SHUTTER_SOUND_COMMAND)
                return if (result.isSuccess) {
                    NotificationHelper.showResult(applicationContext, true, "재부팅/업데이트 후 무음 설정을 다시 적용했습니다.")
                    Result.success()
                } else {
                    NotificationHelper.showResult(applicationContext, false, result.error.ifBlank { "무음 설정 재적용에 실패했습니다." })
                    Result.retry()
                }
            }
            if (attempt < 2) delay(10_000)
        }
        NotificationHelper.showResult(applicationContext, false, "Shizuku가 준비되지 않아 자동 재적용을 완료하지 못했습니다.")
        return Result.retry()
    }
}
