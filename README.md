# 배출기 트리거 (Dispenser Trigger)

갤럭시 탭에서 USB-C ↔ RS-232 어댑터를 통해 배출기에 트리거 신호를 전송하는 Android 앱.

## 기능

- USB-Serial 장치 자동 감지 (FTDI, PL2303, CH340, CP210x)
- 단일 버튼으로 배출기 트리거 명령 전송
- 연결 상태 실시간 표시
- 통신 로그 출력

## 요구사항

### 개발 환경
- Android Studio Hedgehog 이상
- JDK 17
- Gradle 8.5

### 실행 환경
- Android 8.0 (API 26) 이상
- USB Host 기능 지원 기기
- USB-C ↔ RS-232 어댑터

## 빌드 방법

1. Android Studio에서 프로젝트 열기
2. Gradle Sync 실행
3. Build > Make Project
4. Run > Run 'app'

## 프로젝트 구조

```
app/src/main/
├── java/com/dispenser/trigger/
│   ├── ui/
│   │   └── MainActivity.kt       # 메인 화면
│   ├── usb/
│   │   ├── SerialPortManager.kt  # USB-Serial 통신 관리
│   │   └── UsbPermissionHelper.kt # USB 권한 처리
│   └── util/
│       └── Constants.kt          # 상수 정의
├── res/
│   ├── layout/
│   │   └── activity_main.xml     # 메인 화면 레이아웃
│   ├── values/
│   │   ├── colors.xml            # 색상 정의
│   │   ├── strings.xml           # 문자열 정의
│   │   └── themes.xml            # 테마 정의
│   └── xml/
│       └── device_filter.xml     # USB 장치 필터
└── AndroidManifest.xml           # 앱 매니페스트
```

## 설정 변경

### 통신 설정 (Constants.kt)

```kotlin
object SerialConfig {
    const val BAUD_RATE: Int = 9600       // 보 레이트
    const val DATA_BITS: Int = DATABITS_8 // 데이터 비트
    const val STOP_BITS: Int = STOPBITS_1 // 정지 비트
    const val PARITY: Int = PARITY_NONE   // 패리티
}
```

### 트리거 명령 (Constants.kt)

```kotlin
object TriggerCommand {
    // 텍스트 명령
    const val TRIGGER_COMMAND_STRING: String = "TRIGGER\r\n"

    // 바이너리 명령
    val TRIGGER_COMMAND_BYTES: ByteArray = byteArrayOf(0x02, 0x31, 0x03)

    // 사용할 명령 형식 선택
    const val USE_STRING_COMMAND: Boolean = true
}
```

## 지원 USB-Serial 칩셋

| 제조사 | 칩셋 | Vendor ID |
|-------|------|-----------|
| FTDI | FT232R, FT232H | 0x0403 |
| Prolific | PL2303 | 0x067B |
| Silicon Labs | CP2102, CP2104 | 0x10C4 |
| WCH | CH340, CH341 | 0x1A86 |

## 라이선스

내부 사용 전용
# dispenser-trigger-android
