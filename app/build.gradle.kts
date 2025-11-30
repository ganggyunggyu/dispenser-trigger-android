/**
 * build.gradle.kts (app 모듈 레벨)
 *
 * 메인 앱 모듈의 빌드 설정 파일.
 * 컴파일 SDK, 타겟 SDK, 의존성 라이브러리 등을 정의한다.
 *
 * [핵심 설정]
 * - compileSdk: 컴파일 시 사용할 Android SDK 버전
 * - minSdk: 앱이 지원하는 최소 Android 버전 (API 26 = Android 8.0)
 * - targetSdk: 앱이 타겟으로 하는 SDK 버전
 *
 * [의존성 라이브러리]
 * - usb-serial-for-android: USB-Serial 통신 라이브러리 (FTDI/PL2303/CH34x 지원)
 * - AndroidX AppCompat: 하위 호환성 지원
 * - Material Design: UI 컴포넌트
 *
 * [수정 시 참고]
 * - minSdk 26 미만으로 내리면 USB Host API 일부 기능 사용 불가
 * - usb-serial-for-android 버전 업그레이드 시 GitHub 릴리즈 노트 확인 필수
 */

plugins {
    // Android Application 플러그인 적용
    id("com.android.application")
    // Kotlin Android 플러그인 적용
    id("org.jetbrains.kotlin.android")
}

android {
    // 앱의 고유 패키지명 (네임스페이스)
    // 이 값은 AndroidManifest.xml의 package 속성과 동일해야 함
    namespace = "com.dispenser.trigger"

    // 컴파일에 사용할 SDK 버전 (최신 안정 버전 권장)
    compileSdk = 34

    defaultConfig {
        // 앱 고유 식별자 - Play Store 등록 시 유일해야 함
        applicationId = "com.dispenser.trigger"

        // 최소 지원 SDK 버전
        // API 26 (Android 8.0) 이상: USB Host API 안정성 확보
        // 이보다 낮추면 일부 USB 기능이 동작하지 않을 수 있음
        minSdk = 26

        // 타겟 SDK 버전 - 최신 Android 동작 방식 적용
        targetSdk = 34

        // 앱 버전 코드 (정수) - 업데이트 시 반드시 증가
        versionCode = 1

        // 앱 버전 이름 (문자열) - 사용자에게 표시되는 버전
        versionName = "1.0.0"

        // 테스트 러너 설정
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Release 빌드 설정
        release {
            // ProGuard/R8 코드 난독화 비활성화 (MVP 단계에서는 불필요)
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        // Debug 빌드 설정 (기본값 사용)
        debug {
            // 디버그 빌드는 자동으로 debuggable = true
            isDebuggable = true
        }
    }

    // Java/Kotlin 호환성 설정
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // ViewBinding 활성화 - findViewById 대신 타입 안전한 뷰 접근
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // ============================================================
    // USB Serial 통신 라이브러리 (핵심)
    // ============================================================
    // usb-serial-for-android: USB-Serial 어댑터 통신 지원
    // 지원 칩셋: FTDI, PL2303, CH340/CH341, CP210x 등
    // GitHub: https://github.com/mik3y/usb-serial-for-android
    //
    // [왜 이 라이브러리인가]
    // - 가장 널리 사용되는 Android USB Serial 라이브러리
    // - 다양한 칩셋 자동 감지 지원
    // - 활발한 유지보수 및 커뮤니티
    //
    // [대체 가능성]
    // - felHR85/UsbSerial: 비슷한 기능, 더 낮은 레벨 제어 가능
    // - 직접 USB Host API 사용: 복잡하지만 완전한 제어 가능
    implementation("com.github.mik3y:usb-serial-for-android:3.7.0")

    // ============================================================
    // AndroidX 기본 라이브러리
    // ============================================================
    // Core KTX: Kotlin 확장 함수 제공
    implementation("androidx.core:core-ktx:1.12.0")

    // AppCompat: 하위 호환성 지원 (ActionBar, Theme 등)
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Material Design 컴포넌트
    // 버튼, 텍스트필드 등 Material 스타일 UI 제공
    implementation("com.google.android.material:material:1.11.0")

    // ConstraintLayout: 유연한 레이아웃 구성
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Activity KTX: Activity 관련 Kotlin 확장
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Lifecycle 컴포넌트: 생명주기 관리
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // ============================================================
    // 테스트 라이브러리
    // ============================================================
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
