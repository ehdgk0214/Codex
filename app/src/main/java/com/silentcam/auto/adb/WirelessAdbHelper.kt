package com.silentcam.auto.adb

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.wifi.WifiManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address

/** Wireless ADB는 Android 보안 정책상 앱이 페어링 코드를 직접 읽거나 개발자 옵션을 켤 수 없습니다.
 * 대신 현재 Wi-Fi IP, 이동 버튼, 체크리스트를 제공해 사용자가 한 번만 설정하도록 돕습니다.
 */
class WirelessAdbHelper(private val context: Context) {
    fun developerOptionsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun currentWifiIp(): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return fallbackWifiIp()
        val props = cm.getLinkProperties(network) ?: return fallbackWifiIp()
        return props.linkAddresses.firstIpv4Address() ?: fallbackWifiIp()
    }

    private fun List<LinkAddress>.firstIpv4Address(): String? = firstOrNull {
        it.address is Inet4Address && !it.address.isLoopbackAddress
    }?.address?.hostAddress

    @Suppress("DEPRECATION")
    private fun fallbackWifiIp(): String {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return "확인 불가"
        val ip = wifi.connectionInfo?.ipAddress ?: return "확인 불가"
        if (ip == 0) return "확인 불가"
        return listOf(ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff).joinToString(".")
    }
}

class AdbCommandExecutor {
    data class Result(val exitCode: Int, val output: String, val error: String)

    /**
     * 기기에 adb 바이너리를 번들링/설치한 고급 사용자용 실행기입니다.
     * 일반 배포 APK에는 adb 페어링을 자동 완료할 권한이 없어 Shizuku 방식을 권장합니다.
     */
    suspend fun executeWithLocalAdb(command: String, host: String = "127.0.0.1", port: Int = 5555): Result = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("sh", "-c", "adb connect $host:$port >/dev/null && adb shell '$command'")
                .redirectErrorStream(false)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val error = process.errorStream.bufferedReader().use { it.readText().trim() }
            Result(process.waitFor(), output, error)
        }.getOrElse { Result(-1, "", it.localizedMessage ?: "ADB 실행 오류") }
    }
}
