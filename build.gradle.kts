/**
 * build.gradle.kts (프로젝트 레벨)
 *
 * 전체 프로젝트에 적용되는 빌드 설정.
 * 여기서는 Android Gradle Plugin과 Kotlin Plugin 버전을 정의한다.
 *
 * [왜 필요한가]
 * - 모든 서브모듈(app 등)에서 공통으로 사용할 플러그인 버전을 한 곳에서 관리
 * - 버전 충돌 방지 및 일관성 유지
 *
 * [수정 시 참고]
 * - Android Studio 버전 업그레이드 시 AGP(Android Gradle Plugin) 버전도 함께 올려야 함
 * - Kotlin 버전은 AGP 호환성 표 참고: https://developer.android.com/studio/releases/gradle-plugin
 */

plugins {
    // Android Application 플러그인 - 앱 모듈 빌드에 필요
    // apply false: 여기서는 선언만 하고, 실제 적용은 각 모듈에서
    id("com.android.application") version "8.12.3" apply false

    // Kotlin Android 플러그인 - Kotlin 언어 지원
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
