/**
 * UsbPermissionManager.kt
 *
 * Activity 생명주기에 맞춰 USB 권한을 관리하는 인스턴스 기반 클래스.
 *
 * [싱글톤 UsbPermissionHelper와의 차이]
 * - UsbPermissionHelper: 앱 전역에서 간단하게 사용
 * - UsbPermissionManager: Activity별로 독립적인 권한 관리 필요 시 사용
 *
 * [사용 예시]
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     private lateinit var permissionManager: UsbPermissionManager
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         permissionManager = UsbPermissionManager(this, object : ... {
 *             // 콜백 구현
 *         })
 *     }
 *
 *     override fun onDestroy() {
 *         permissionManager.unregister()
 *         super.onDestroy()
 *     }
 * }
 * ```
 *
 * @param context Activity 컨텍스트
 * @param callback 권한 결과 콜백
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
import com.dispenser.trigger.util.UsbConfig
import com.dispenser.trigger.util.getUsbDevice

class UsbPermissionManager(
    private val context: Context,
    private val callback: UsbPermissionHelper.UsbPermissionCallback
) {
    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != UsbConfig.ACTION_USB_PERMISSION) return

            intent.getUsbDevice()?.let { device ->
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                callback.onPermissionResult(device, granted)
            }
        }
    }

    private var isRegistered = false

    init {
        register()
    }

    private fun register() {
        if (isRegistered) return

        val filter = IntentFilter(UsbConfig.ACTION_USB_PERMISSION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }

        isRegistered = true
    }

    /**
     * Receiver 해제
     *
     * Activity의 onDestroy()에서 반드시 호출 필요
     * 미호출 시 메모리 누수 발생
     */
    fun unregister() {
        if (!isRegistered) return

        try {
            context.unregisterReceiver(permissionReceiver)
            isRegistered = false
        } catch (e: IllegalArgumentException) {
            // 이미 해제됨
        }
    }

    /**
     * 권한 요청
     *
     * @param device 권한 요청할 장치
     */
    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            callback.onPermissionResult(device, true)
            return
        }

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

    /**
     * 권한 확인
     */
    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)
}
