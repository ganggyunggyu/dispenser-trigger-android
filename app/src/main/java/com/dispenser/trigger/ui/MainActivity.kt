package com.dispenser.trigger.ui

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dispenser.trigger.R
import com.dispenser.trigger.databinding.ActivityMainBinding
import com.dispenser.trigger.usb.SerialPortManager
import com.dispenser.trigger.usb.UsbEventHandler
import com.dispenser.trigger.usb.UsbPermissionHelper
import com.dispenser.trigger.util.LogConfig
import com.dispenser.trigger.util.UiConfig

/**
 * 메인 Activity - USB-Serial 연결 관리 및 트리거 버튼 처리
 */
class MainActivity : AppCompatActivity(),
    SerialPortManager.SerialPortListener,
    UsbEventHandler.UsbEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var serialPortManager: SerialPortManager
    private lateinit var logManager: LogManager
    private lateinit var usbEventHandler: UsbEventHandler

    private var lastClickTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        serialPortManager = SerialPortManager(this, this)
        logManager = LogManager(binding.logTextView, binding.logScrollView)
        usbEventHandler = UsbEventHandler(this, this)

        setupClickListeners()
        usbEventHandler.register()

        updateConnectionStatus(false)
        logManager.append("앱 시작됨")
        logManager.append("USB-Serial 장치를 연결해주세요")

        usbEventHandler.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        usbEventHandler.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateConnectionStatus(serialPortManager.isConnected())

        if (!serialPortManager.isConnected()) {
            logManager.append("USB 장치 재탐색 중...")
            attemptAutoConnect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serialPortManager.disconnect()
        usbEventHandler.unregister()
        UsbPermissionHelper.unregister(this)
        Log.d(LogConfig.TAG, "MainActivity 종료됨")
    }

    private fun setupClickListeners() {
        binding.triggerButton.setOnClickListener { onTriggerButtonClick() }
        binding.connectButton?.setOnClickListener { onConnectButtonClick() }
        binding.clearLogButton?.setOnClickListener { logManager.clear() }
    }

    private fun onTriggerButtonClick() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < UiConfig.BUTTON_DEBOUNCE_MS) {
            logManager.append("버튼 연속 클릭 무시됨")
            return
        }
        lastClickTime = currentTime

        if (!serialPortManager.isConnected()) {
            logManager.append("⚠️ 연결되지 않은 상태입니다")
            showNotConnectedWarning()
            return
        }

        logManager.append("트리거 명령 전송 중...")
        if (serialPortManager.sendTriggerCommand()) {
            animateTriggerButton()
        }
    }

    private fun onConnectButtonClick() {
        if (serialPortManager.isConnected()) {
            serialPortManager.disconnect()
        } else {
            attemptAutoConnect()
        }
    }

    private fun animateTriggerButton() {
        binding.triggerButton.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(50)
            .withEndAction {
                binding.triggerButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(50)
                    .start()
            }
            .start()
    }

    private fun showNotConnectedWarning() {
        binding.statusTextView.animate()
            .alpha(0.3f)
            .setDuration(100)
            .withEndAction {
                binding.statusTextView.animate()
                    .alpha(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    // UsbEventHandler.UsbEventListener 구현
    override fun onDeviceAttached(device: UsbDevice) {
        logManager.append("USB 장치 연결됨: ${device.productName ?: device.deviceName}")

        if (serialPortManager.hasPermission(device)) {
            connectToDevice(device)
        } else {
            logManager.append("USB 권한 요청 중...")
            requestUsbPermission(device)
        }
    }

    override fun onDeviceDetached(device: UsbDevice) {
        logManager.append("USB 장치 분리됨: ${device.productName ?: device.deviceName}")

        if (serialPortManager.isConnected()) {
            serialPortManager.disconnect()
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        UsbPermissionHelper.requestPermission(this, device) { granted ->
            runOnUiThread {
                if (granted) {
                    logManager.append("USB 권한 허용됨")
                    connectToDevice(device)
                } else {
                    logManager.append("⚠️ USB 권한 거부됨")
                    logManager.append("설정에서 USB 권한을 허용해주세요")
                }
            }
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        logManager.append("장치 연결 시도 중...")
        serialPortManager.connect(device)
    }

    private fun attemptAutoConnect() {
        val drivers = serialPortManager.findDevices()

        when {
            drivers.isEmpty() -> {
                logManager.append("USB-Serial 장치가 없습니다")
                logManager.append("USB-C ↔ RS-232 어댑터를 연결해주세요")
            }
            drivers.size == 1 -> {
                val device = drivers.first().device
                if (serialPortManager.hasPermission(device)) {
                    serialPortManager.connect(drivers.first())
                } else {
                    logManager.append("USB 권한 요청 중...")
                    requestUsbPermission(device)
                }
            }
            else -> {
                logManager.append("${drivers.size}개 장치 발견, 첫 번째 장치에 연결합니다")
                val device = drivers.first().device
                if (serialPortManager.hasPermission(device)) {
                    serialPortManager.connect(drivers.first())
                } else {
                    requestUsbPermission(device)
                }
            }
        }
    }

    // SerialPortManager.SerialPortListener 구현
    override fun onConnected(deviceName: String) {
        runOnUiThread {
            logManager.append("✓ 연결됨: $deviceName")
            updateConnectionStatus(true)
        }
    }

    override fun onDisconnected(reason: String) {
        runOnUiThread {
            logManager.append("연결 해제: $reason")
            updateConnectionStatus(false)
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            logManager.append("⚠️ 오류: $error")
        }
    }

    override fun onDataSent(bytesSent: Int) {
        runOnUiThread {
            logManager.append("✓ 전송 완료: ${bytesSent}바이트")
        }
    }

    override fun onDataReceived(data: ByteArray) {
        runOnUiThread {
            val hexString = data.joinToString(" ") { String.format("%02X", it) }
            logManager.append("수신: $hexString")
        }
    }

    override fun onLog(message: String) {
        runOnUiThread {
            logManager.append(message)
        }
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        if (isConnected) {
            val deviceName = serialPortManager.getConnectedDeviceName() ?: "알 수 없는 장치"
            binding.statusTextView.text = getString(R.string.status_connected, deviceName)
            binding.statusTextView.setTextColor(ContextCompat.getColor(this, R.color.connected_green))
            binding.triggerButton.isEnabled = true
            binding.triggerButton.alpha = 1.0f
            binding.connectButton?.text = getString(R.string.disconnect)
        } else {
            binding.statusTextView.text = getString(R.string.status_disconnected)
            binding.statusTextView.setTextColor(ContextCompat.getColor(this, R.color.disconnected_red))
            binding.triggerButton.isEnabled = true
            binding.triggerButton.alpha = 0.5f
            binding.connectButton?.text = getString(R.string.connect)
        }
    }
}
