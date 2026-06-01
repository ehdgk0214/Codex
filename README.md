# 무음카메라 자동설정 (SilentCam Auto)

삼성 갤럭시에서 아래 설정 명령을 Shizuku 우선으로 원터치 실행하는 Kotlin + Jetpack Compose 앱입니다.

```bash
# ?? ??
settings put system csc_pref_camera_forced_shuttersound_key 0

# ?? ????? ????
settings put system csc_pref_camera_forced_shuttersound_key 1
```

## 주요 기능

- One UI 느낌의 Material 3 / Material You Compose 메인 화면
- 현재 `csc_pref_camera_forced_shuttersound_key` 값 확인 및 상태 표시
- Shizuku 설치/서비스/권한 상태 표시와 권한 요청
- 버튼 한 번으로 Shizuku shell 명령 실행
- Wireless ADB 페어링 도우미: 현재 Wi‑Fi IP 표시, 개발자 옵션 이동, 단계별 안내
- WorkManager 기반 재부팅/앱 업데이트 후 자동 재적용 옵션
- 성공/실패 알림과 화면 내 실시간 로그
- 다크 모드 및 Android 12+ 다이나믹 컬러 지원

## 프로젝트 구조

```text
app/src/main/java/com/silentcam/auto/
├── MainActivity.kt                    # Compose 화면 진입점
├── MainViewModel.kt                   # MVVM 상태/이벤트/명령 실행 흐름
├── CameraSetting.kt                   # 삼성 카메라 설정 명령 상수
├── SettingsRepository.kt              # DataStore 설정 저장소
├── shizuku/ShizukuCommandExecutor.kt  # Shizuku API 연동 및 shell 실행
├── adb/WirelessAdbHelper.kt           # Wireless ADB IP/가이드/고급 실행기
├── worker/ApplySilentCameraWorker.kt  # 재부팅 후 재적용 WorkManager 작업
├── worker/BootCompletedReceiver.kt    # 부팅/업데이트 브로드캐스트 수신
├── notifications/NotificationHelper.kt# 결과 알림 채널/표시
└── ui/theme/Theme.kt                  # Material You 테마
```

## 주의

Wireless ADB의 페어링 코드 자동 읽기, 개발자 옵션 자동 활성화는 Android 보안 정책상 일반 앱에서 불가능합니다. 그래서 앱은 Shizuku를 1순위로 사용하고, Wireless ADB는 사용자가 Shizuku를 시작할 수 있도록 안내하는 보조 기능으로 제공합니다.

## APK 다운로드 방법 (GitHub Actions)

이 저장소는 GitHub Actions로 디버그 APK를 자동 빌드합니다.

1. GitHub 저장소의 **Actions** 탭을 엽니다.
2. **Build Android debug APK** 워크플로를 선택합니다.
3. 최신 성공 실행을 열거나, **Run workflow** 버튼으로 직접 실행합니다.
4. 실행이 끝나면 하단 **Artifacts** 영역에서 `SilentCamAuto-debug-apk`를 다운로드합니다.
5. 압축을 풀면 `app-debug.apk`가 있으며, 갤럭시 기기에 복사해 설치할 수 있습니다.

디버그 APK는 테스트 설치용입니다. Play 스토어 배포나 장기 배포에는 별도의 릴리스 서명 APK/AAB 구성이 필요합니다.
