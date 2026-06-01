package com.silentcam.auto

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silentcam.auto.ui.theme.SilentCamAutoTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SilentCamAutoTheme {
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                SilentCamApp(viewModel = mainViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshAll()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilentCamApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("무음카메라 자동설정", fontWeight = FontWeight.Bold)
                        Text("SilentCam Auto", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroStatusCard(state)

            Button(
                onClick = { viewModel.requestShizukuPermissionOrApply() },
                enabled = !state.isApplying,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (state.isApplying) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Default.VolumeOff, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("셔터음 무음 적용하기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            ShizukuCard(
                state = state,
                onInstall = {
                    runCatching { context.startActivity(viewModel.openShizukuInstall()) }
                        .onFailure { context.startActivity(viewModel.openShizukuWeb()) }
                },
                onRequest = { viewModel.requestShizukuPermissionOrApply() }
            )

            AutomationCard(
                autoApply = state.autoApplyAfterBoot,
                onAutoApplyChange = viewModel::setAutoApplyAfterBoot,
                onRecheck = viewModel::checkCurrentStatus
            )

            WirelessAdbCard(
                ip = state.wifiIp,
                onOpenDeveloperOptions = {
                    runCatching { context.startActivity(viewModel.openDeveloperOptions()) }
                        .onFailure { Toast.makeText(context, "개발자 옵션 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show() }
                }
            )

            LogCard(logs = state.logs)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroStatusCard(state: MainUiState) {
    val color = when (state.shutterStatus) {
        ShutterStatus.Muted -> MaterialTheme.colorScheme.primary
        ShutterStatus.Forced -> MaterialTheme.colorScheme.error
        ShutterStatus.Unknown -> MaterialTheme.colorScheme.tertiary
    }
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VolumeOff, contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("현재 셔터음 상태", style = MaterialTheme.typography.labelLarge)
                Text(state.shutterStatus.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("설정값이 0이면 삼성 카메라 강제 셔터음이 해제된 상태입니다.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ShizukuCard(state: MainUiState, onInstall: () -> Unit, onRequest: () -> Unit) {
    InfoCard(title = "1순위 자동화: Shizuku", icon = { Icon(Icons.Default.CheckCircle, null) }) {
        Text("ADB를 매번 PC에 연결하지 않고도 shell 권한으로 명령을 실행합니다. 가장 안정적인 원터치 방식입니다.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("설치", state.shizukuInstalled)
            StatusChip("서비스", state.shizukuReady)
            StatusChip("권한", state.shizukuPermissionGranted)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onInstall) { Text("Shizuku 설치") }
            FilledTonalButton(onClick = onRequest) { Text("권한 요청/적용") }
        }
    }
}

@Composable
private fun AutomationCard(autoApply: Boolean, onAutoApplyChange: (Boolean) -> Unit, onRecheck: () -> Unit) {
    InfoCard(title = "재부팅/업데이트 후 자동 재적용", icon = { Icon(Icons.Default.Settings, null) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("WorkManager가 부팅 또는 앱 업데이트 후 Shizuku 권한이 살아 있으면 다시 적용합니다.")
                Text("시스템 업데이트 후에는 이 앱을 열고 버튼을 한 번 더 누르면 즉시 복구됩니다.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = autoApply, onCheckedChange = onAutoApplyChange)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRecheck) { Text("현재 상태 다시 확인") }
    }
}

@Composable
private fun WirelessAdbCard(ip: String, onOpenDeveloperOptions: () -> Unit) {
    InfoCard(title = "2순위: Wireless ADB 페어링 도우미", icon = { Icon(Icons.Default.Info, null) }) {
        Text("현재 Wi‑Fi IP: $ip", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("안드로이드 보안상 앱이 페어링 코드를 몰래 읽거나 개발자 옵션을 자동으로 켤 수는 없습니다. 아래 순서로 한 번만 준비해 주세요.")
        Spacer(Modifier.height(8.dp))
        Text("1. 개발자 옵션 → 무선 디버깅 켜기\n2. '페어링 코드로 기기 페어링' 선택\n3. PC/Termux에서 adb pair IP:포트 코드 입력\n4. 이후에는 Shizuku를 무선 디버깅으로 시작")
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onOpenDeveloperOptions) { Text("개발자 옵션 열기") }
    }
}

@Composable
private fun LogCard(logs: List<String>) {
    InfoCard(title = "실행 로그", icon = { Icon(Icons.Default.Info, null) }) {
        if (logs.isEmpty()) Text("아직 로그가 없습니다. 버튼을 누르면 실행 과정이 여기에 표시됩니다.")
        else LazyColumn(modifier = Modifier.height(180.dp), reverseLayout = false) {
            items(logs) { log -> Text(log, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun InfoCard(title: String, icon: @Composable () -> Unit, content: @Composable Column.() -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    AssistChip(
        onClick = { },
        label = { Text("$label ${if (ok) "OK" else "필요"}") },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (ok) Color(0xFF34C759) else MaterialTheme.colorScheme.error)
            )
        }
    )
}
