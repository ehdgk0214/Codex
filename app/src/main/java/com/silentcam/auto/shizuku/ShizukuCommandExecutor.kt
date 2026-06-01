package com.silentcam.auto.shizuku

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku를 통해 adb shell 권한과 동일한 프로세스를 실행하는 헬퍼입니다.
 * 삼성 카메라 셔터음 설정은 일반 앱 권한으로 변경할 수 없으므로 Shizuku가 1순위입니다.
 */
class ShizukuCommandExecutor(private val context: Context) {
    data class Result(val exitCode: Int, val output: String, val error: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun isReady(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = isReady() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun shouldShowRequestRationale(): Boolean = isReady() && Shizuku.shouldShowRequestPermissionRationale()

    fun requestPermission(requestCode: Int) {
        if (isReady()) Shizuku.requestPermission(requestCode)
    }

    suspend fun execute(command: String): Result = withContext(Dispatchers.IO) {
        if (!isReady()) return@withContext Result(-1, "", "Shizuku 서비스가 실행 중이 아닙니다.")
        if (!hasPermission()) return@withContext Result(-2, "", "Shizuku 권한이 아직 승인되지 않았습니다.")

        runCatching {
            // Shizuku.newProcess는 앱 프로세스가 아닌 adb shell 컨텍스트에서 명령을 실행합니다.
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val stdout = asyncRead(process.inputStream.bufferedReader())
            val stderr = asyncRead(process.errorStream.bufferedReader())
            val exit = process.waitFor()
            Result(exit, stdout, stderr)
        }.getOrElse { throwable ->
            Result(-3, "", throwable.localizedMessage ?: "알 수 없는 Shizuku 실행 오류")
        }
    }

    private fun asyncRead(reader: BufferedReader): String = reader.use { it.readText().trim() }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val PLAY_STORE_URL = "market://details?id=$SHIZUKU_PACKAGE"
        const val WEB_URL = "https://shizuku.rikka.app/download/"
    }
}
