package com.dispenser.trigger.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.dispenser.trigger.util.LogConfig
import com.dispenser.trigger.util.getUsbDevice

/**
 * USB 연결/해제 이벤트를 처리하는 핸들러
 *
 * @param context 앱 컨텍스트
 * @param listener USB 이벤트 콜백
 */
class UsbEventHandler(
    private val context: Context,
    private val listener: UsbEventListener
) {
    interface UsbEventListener {
        fun onDeviceAttached(device: UsbDevice)
        fun onDeviceDetached(device: UsbDevice)
    }

    private val usbEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(LogConfig.TAG, "USB 이벤트 수신: ${intent.action}")

            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    intent.getUsbDevice()?.let { listener.onDeviceAttached(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    intent.getUsbDevice()?.let { listener.onDeviceDetached(it) }
                }
            }
        }
    }

    /**
     * BroadcastReceiver 등록
     */
    fun register() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbEventReceiver, filter)
        }

        Log.d(LogConfig.TAG, "USB 이벤트 Receiver 등록 완료")
    }

    /**
     * BroadcastReceiver 해제
     */
    fun unregister() {
        try {
            context.unregisterReceiver(usbEventReceiver)
        } catch (e: IllegalArgumentException) {
            // 이미 해제됨
        }
    }

    /**
     * 인텐트에서 USB 장치 연결 이벤트 처리
     */
    fun handleIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            intent.getUsbDevice()?.let { listener.onDeviceAttached(it) }
        }
    }
}
