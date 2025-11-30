# ProGuard/R8 설정 파일
#
# [역할]
# - 코드 난독화 규칙 정의
# - 사용하지 않는 코드 제거 규칙
# - 특정 클래스/메서드 보존 규칙
#
# [현재 상태]
# - MVP 단계에서는 난독화 비활성화 (build.gradle.kts에서 isMinifyEnabled = false)
# - 릴리즈 빌드에서 난독화 활성화 시 이 파일의 규칙 적용
#
# [수정 시 참고]
# - USB Serial 라이브러리 관련 클래스는 보존 필요
# - Reflection 사용 클래스 보존 필요

# ============================================================
# USB Serial 라이브러리 보존 규칙
# ============================================================

# usb-serial-for-android 라이브러리 클래스 보존
# 드라이버 클래스들이 리플렉션으로 로드되므로 난독화하면 안 됨
-keep class com.hoho.android.usbserial.** { *; }

# ============================================================
# Android 기본 보존 규칙
# ============================================================

# Parcelable 클래스 보존
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Serializable 클래스 보존
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================
# 디버깅을 위한 설정
# ============================================================

# 스택 트레이스에서 줄 번호 유지 (크래시 분석용)
-keepattributes SourceFile,LineNumberTable

# 원본 소스 파일명 숨기기 (선택적)
# -renamesourcefileattribute SourceFile
