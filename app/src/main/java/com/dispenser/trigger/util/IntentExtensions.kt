package com.dispenser.trigger.util

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build

/**
 * Intent 관련 확장 함수 모음
 *
 * Android API 버전에 따른 분기 처리를 캡슐화하여
 * 코드 중복을 방지하고 유지보수성을 높입니다.
 */

/**
 * Intent에서 UsbDevice를 안전하게 추출
 *
 * Android 13(TIRAMISU) 이상에서는 타입 안전한 getParcelableExtra 사용
 * 그 이하 버전에서는 deprecated API 사용
 *
 * @return UsbDevice 또는 null
 */
fun Intent.getUsbDevice(): UsbDevice? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
}
