/**
 * UsbPermissionHelper.kt
 *
 * USB 장치 접근 권한 요청 및 관리를 담당하는 싱글톤 헬퍼 클래스.
 *
 * [왜 권한이 필요한가]
 * - Android에서 USB 장치 접근은 보안상 사용자 동의 필요
 * - 앱이 USB 장치에 접근하려면 먼저 requestPermission() 호출
 * - 사용자가 "허용"하면 그 이후로 해당 장치 접근 가능
 *
 * [권한 흐름]
 * 1. 앱에서 특정 USB 장치 접근 시도
 * 2. hasPermission() 확인 → 권한 없음
 * 3. requestPermission() 호출 → 시스템 다이얼로그 표시
 * 4. 사용자가 허용/거부 선택
 * 5. BroadcastReceiver로 결과 수신
 * 6. 허용된 경우 장치 연결 진행
 *
 * [수정 시 참고]
 * - ACTION_USB_PERMISSION은 Constants.kt에서 정의
 * - 권한은 앱 재설치 또는 장치 재연결 시 초기화될 수 있음
 * - 여러 장치 지원 시 각 장치별로 권한 요청 필요
 * - Activity 생명주기에 맞춘 관리가 필요하면 UsbPermissionManager 사용
 */
package com.dispenser.trigger.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.dispenser.trigger.util.LogConfig
import com.dispenser.trigger.util.UsbConfig
import com.dispenser.trigger.util.getUsbDevice

/**
 * USB 권한 관리 싱글톤 헬퍼
 *
 * [사용법]
 * ```kotlin
 * UsbPermissionHelper.requestPermission(context, device) { granted ->
 *     if (granted) {
 *         serialPortManager.connect(device)
 *     } else {
 *         showError("USB 권한이 거부되었습니다")
 *     }
 * }
 * ```
 */
object UsbPermissionHelper {

    /**
     * 권한 요청 결과 콜백
     */
    interface UsbPermissionCallback {
        fun onPermissionResult(device: UsbDevice, granted: Boolean)
    }

    private val pendingCallbacks = mutableMapOf<Int, UsbPermissionCallback>()
    private var isReceiverRegistered = false

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(LogConfig.TAG, "USB 권한 브로드캐스트 수신")

            if (intent.action != UsbConfig.ACTION_USB_PERMISSION) {
                Log.w(LogConfig.TAG, "알 수 없는 액션: ${intent.action}")
                return
            }

            val device = intent.getUsbDevice()
            if (device == null) {
                Log.e(LogConfig.TAG, "인텐트에 장치 정보 없음")
                return
            }

            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            Log.d(LogConfig.TAG, "권한 결과 - 장치: ${device.productName}, 허용: $granted")

            pendingCallbacks.remove(device.deviceId)?.onPermissionResult(device, granted)
        }
    }

    /**
     * USB 장치 권한 요청 (람다 버전)
     */
    fun requestPermission(
        context: Context,
        device: UsbDevice,
        onResult: (granted: Boolean) -> Unit
    ) {
        requestPermission(context, device, object : UsbPermissionCallback {
            override fun onPermissionResult(device: UsbDevice, granted: Boolean) {
                onResult(granted)
            }
        })
    }

    /**
     * USB 장치 권한 요청 (인터페이스 버전)
     */
    fun requestPermission(
        context: Context,
        device: UsbDevice,
        callback: UsbPermissionCallback
    ) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        if (usbManager.hasPermission(device)) {
            Log.d(LogConfig.TAG, "이미 권한이 있음: ${device.productName}")
            callback.onPermissionResult(device, true)
            return
        }

        Log.d(LogConfig.TAG, "권한 요청 시작: ${device.productName}")
        registerReceiverIfNeeded(context)
        pendingCallbacks[device.deviceId] = callback

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent(UsbConfig.ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        usbManager.requestPermission(device, permissionIntent)
    }

    private fun registerReceiverIfNeeded(context: Context) {
        if (isReceiverRegistered) return

        val filter = IntentFilter(UsbConfig.ACTION_USB_PERMISSION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                usbPermissionReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(usbPermissionReceiver, filter)
        }

        isReceiverRegistered = true
        Log.d(LogConfig.TAG, "USB 권한 Receiver 등록 완료")
    }

    /**
     * BroadcastReceiver 해제
     */
    fun unregister(context: Context) {
        if (!isReceiverRegistered) return

        try {
            context.unregisterReceiver(usbPermissionReceiver)
            isReceiverRegistered = false
            pendingCallbacks.clear()
            Log.d(LogConfig.TAG, "USB 권한 Receiver 해제 완료")
        } catch (e: IllegalArgumentException) {
            Log.w(LogConfig.TAG, "Receiver 이미 해제됨")
        }
    }

    /**
     * 특정 장치의 권한 확인
     */
    fun hasPermission(context: Context, device: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.hasPermission(device)
    }
}
