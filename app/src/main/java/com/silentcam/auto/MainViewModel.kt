package com.silentcam.auto

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.silentcam.auto.adb.WirelessAdbHelper
import com.silentcam.auto.notifications.NotificationHelper
import com.silentcam.auto.shizuku.ShizukuCommandExecutor
import com.silentcam.auto.worker.ApplySilentCameraWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MainUiState(
    val shutterStatus: ShutterStatus = ShutterStatus.Unknown,
    val isApplying: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuReady: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val autoApplyAfterBoot: Boolean = true,
    val wifiIp: String = "확인 중...",
    val logs: List<String> = emptyList()
)

enum class ShutterStatus(val label: String) {
    Muted("무음"), Forced("셔터음 활성화"), Unknown("확인 필요")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val shizuku = ShizukuCommandExecutor(application)
    private val wirelessAdb = WirelessAdbHelper(application)
    private val preferences = SettingsRepository(application)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
        addLog(if (granted) "Shizuku 권한이 승인되었습니다." else "Shizuku 권한이 거부되었습니다.")
        refreshShizukuState()
        if (granted) applySilentMode()
    }

    init {
        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        viewModelScope.launch {
            preferences.autoApplyAfterBoot.collect { enabled ->
                _uiState.update { it.copy(autoApplyAfterBoot = enabled) }
            }
        }
        refreshAll()
    }

    fun refreshAll() {
        refreshShizukuState()
        _uiState.update { it.copy(wifiIp = wirelessAdb.currentWifiIp()) }
        checkCurrentStatus()
    }

    fun refreshShizukuState() {
        _uiState.update {
            it.copy(
                shizukuInstalled = shizuku.isInstalled(),
                shizukuReady = shizuku.isReady(),
                shizukuPermissionGranted = shizuku.hasPermission()
            )
        }
    }

    fun requestShizukuPermissionOrApply() {
        refreshShizukuState()
        when {
            !shizuku.isInstalled() -> addLog("Shizuku가 설치되어 있지 않습니다. 설치 후 Shizuku를 실행해 주세요.")
            !shizuku.isReady() -> addLog("Shizuku 앱에서 '시작'을 먼저 눌러 서비스를 실행해 주세요.")
            shizuku.hasPermission() -> applySilentMode()
            shizuku.shouldShowRequestRationale() -> addLog("Shizuku 권한이 필요합니다. 권한 요청 창에서 허용을 선택해 주세요.")
            else -> shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    fun applySilentMode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true) }
            addLog("명령 실행: ${CameraSetting.DISABLE_FORCED_SHUTTER_SOUND_COMMAND}")
            val result = shizuku.execute(CameraSetting.DISABLE_FORCED_SHUTTER_SOUND_COMMAND)
            if (result.isSuccess) {
                addLog("성공: 삼성 카메라 강제 셔터음 설정을 0으로 변경했습니다.")
                _uiState.update { it.copy(shutterStatus = ShutterStatus.Muted) }
                NotificationHelper.showResult(getApplication(), true, "카메라 셔터음 강제 설정을 해제했습니다.")
            } else {
                val message = result.error.ifBlank { result.output.ifBlank { "종료 코드 ${result.exitCode}" } }
                addLog("실패: $message")
                NotificationHelper.showResult(getApplication(), false, message)
            }
            _uiState.update { it.copy(isApplying = false) }
        }
    }

    fun checkCurrentStatus() {
        viewModelScope.launch {
            if (!shizuku.hasPermission()) {
                _uiState.update { it.copy(shutterStatus = ShutterStatus.Unknown) }
                return@launch
            }
            val result = shizuku.execute(CameraSetting.READ_COMMAND)
            val status = when (result.output.trim()) {
                "0" -> ShutterStatus.Muted
                "1" -> ShutterStatus.Forced
                else -> ShutterStatus.Unknown
            }
            _uiState.update { it.copy(shutterStatus = status) }
            addLog("현재 설정값: ${result.output.ifBlank { "읽기 실패" }}")
        }
    }

    fun setAutoApplyAfterBoot(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoApplyAfterBoot(enabled)
            if (enabled) {
                val request = OneTimeWorkRequestBuilder<ApplySilentCameraWorker>().build()
                WorkManager.getInstance(getApplication()).enqueueUniqueWork("manual-reapply", ExistingWorkPolicy.REPLACE, request)
                addLog("재부팅/앱 업데이트 후 자동 재적용을 켰습니다.")
            } else {
                addLog("자동 재적용을 껐습니다.")
            }
        }
    }

    fun openShizukuInstall(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuCommandExecutor.PLAY_STORE_URL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openShizukuWeb(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuCommandExecutor.WEB_URL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openDeveloperOptions(): Intent = wirelessAdb.developerOptionsIntent()

    private fun addLog(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        _uiState.update { state ->
            state.copy(logs = (listOf("[$stamp] $message") + state.logs).take(80))
        }
    }

    override fun onCleared() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        super.onCleared()
    }

    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 2304
    }
}
