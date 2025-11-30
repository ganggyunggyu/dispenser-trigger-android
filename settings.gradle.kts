/**
 * settings.gradle.kts
 *
 * Gradle 프로젝트 설정 파일.
 * 프로젝트 이름과 포함할 모듈을 정의한다.
 *
 * [구성 요소]
 * - pluginManagement: Gradle 플러그인을 가져올 저장소 설정
 * - dependencyResolutionManagement: 의존성을 가져올 저장소 설정
 * - rootProject.name: 프로젝트 루트 이름
 * - include: 빌드에 포함할 모듈 목록
 */

pluginManagement {
    repositories {
        // Google Maven 저장소 - Android 관련 플러그인 제공
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Maven Central - 일반 Java/Kotlin 라이브러리
        mavenCentral()
        // Gradle 플러그인 포털
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 저장소 모드: 모든 모듈에서 여기 정의된 저장소만 사용하도록 강제
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack - usb-serial-for-android 라이브러리를 가져오기 위해 필요
        maven { url = uri("https://jitpack.io") }
    }
}

// 프로젝트 루트 이름 설정
rootProject.name = "DispenserTrigger"

// app 모듈 포함 (메인 Android 앱)
include(":app")
