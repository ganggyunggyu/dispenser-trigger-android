/**
 * SerialPortManager.kt
 *
 * USB-Serial 포트의 연결, 설정, 통신을 담당하는 관리자 클래스.
 *
 * [역할]
 * 1. USB-Serial 장치 탐색 및 드라이버 자동 감지
 * 2. 포트 연결 및 통신 파라미터 설정 (Baud rate, Data bits 등)
 * 3. 데이터 송신 (Write)
 * 4. 데이터 수신 (Read) - 선택적
 * 5. 연결 상태 관리 및 자원 해제
 *
 * [설계 원칙]
 * - 단일 책임: USB-Serial 통신만 담당
 * - 콜백 패턴: 상태 변화를 외부에 알림
 * - 스레드 안전: 통신은 별도 스레드에서 처리
 *
 * [의존성]
 * - usb-serial-for-android 라이브러리
 * - Constants.kt의 설정값
 *
 * [수정 시 참고]
 * - 새로운 칩셋 지원: UsbSerialProber에 커스텀 드라이버 추가
 * - 양방향 통신: startReading() 메서드 활성화
 * - 다중 포트: 포트 선택 로직 추가
 */
package com.dispenser.trigger.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.dispenser.trigger.util.LogConfig
import com.dispenser.trigger.util.SerialConfig
import com.dispenser.trigger.util.TriggerCommand
import com.dispenser.trigger.util.UsbConfig
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

/**
 * USB-Serial 포트 관리자
 *
 * [사용 방법]
 * 1. 인스턴스 생성: SerialPortManager(context, listener)
 * 2. 장치 탐색: findDevices()
 * 3. 연결: connect(device) 또는 connectFirst()
 * 4. 명령 전송: sendTriggerCommand()
 * 5. 종료: disconnect()
 *
 * [생명주기]
 * - Activity/Fragment의 onDestroy()에서 반드시 disconnect() 호출
 * - 미호출 시 USB 포트가 계속 점유되어 다른 앱에서 사용 불가
 *
 * @param context 앱 컨텍스트 (USB 서비스 접근용)
 * @param listener 연결 상태 변화 콜백
 */
class SerialPortManager(
    private val context: Context,
    private val listener: SerialPortListener
) {
    /**
     * 연결 상태 변화 및 이벤트 콜백 인터페이스
     *
     * [왜 인터페이스로 분리했나]
     * - 느슨한 결합: SerialPortManager가 UI 레이어를 몰라도 됨
     * - 테스트 용이: Mock 리스너로 테스트 가능
     * - 유연성: 여러 곳에서 같은 이벤트 수신 가능
     */
    interface SerialPortListener {
        /**
         * 연결 성공 시 호출
         * @param deviceName 연결된 장치 이름 (예: "FT232R")
         */
        fun onConnected(deviceName: String)

        /**
         * 연결 해제 시 호출
         * @param reason 해제 이유 (정상 종료, 오류 등)
         */
        fun onDisconnected(reason: String)

        /**
         * 오류 발생 시 호출
         * @param error 오류 메시지
         */
        fun onError(error: String)

        /**
         * 데이터 전송 완료 시 호출
         * @param bytesSent 전송된 바이트 수
         */
        fun onDataSent(bytesSent: Int)

        /**
         * 데이터 수신 시 호출 (선택적 기능)
         * @param data 수신된 데이터
         */
        fun onDataReceived(data: ByteArray)

        /**
         * 로그 메시지 발생 시 호출
         * @param message 로그 메시지
         */
        fun onLog(message: String)
    }

    // ============================================================
    // 멤버 변수
    // ============================================================

    /**
     * Android USB 시스템 서비스
     *
     * [역할]
     * - 연결된 USB 장치 목록 조회
     * - USB 장치 연결 열기
     * - USB 권한 요청
     */
    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    /**
     * 현재 사용 중인 USB-Serial 드라이버
     *
     * [드라이버란]
     * - 특정 칩셋(FTDI, CH340 등)과 통신하는 방법을 알고 있는 클래스
     * - usb-serial-for-android가 주요 칩셋용 드라이버 제공
     *
     * [null인 경우]
     * - 아직 장치에 연결되지 않음
     * - 연결이 해제됨
     */
    private var driver: UsbSerialDriver? = null

    /**
     * 현재 열려있는 USB 장치 연결
     *
     * [역할]
     * - USB 장치와의 저수준 통신 채널
     * - 포트를 열기 전에 먼저 이 연결이 필요
     *
     * [주의]
     * - 사용 후 반드시 close() 호출
     * - close()하면 관련 포트도 함께 닫힘
     */
    private var connection: UsbDeviceConnection? = null

    /**
     * 현재 열려있는 시리얼 포트
     *
     * [역할]
     * - 실제 데이터 송수신이 이루어지는 객체
     * - read(), write() 메서드로 통신
     *
     * [포트 번호]
     * - 대부분의 어댑터는 포트 1개만 있음 (index 0)
     * - 일부 멀티포트 어댑터는 여러 포트 제공
     */
    private var port: UsbSerialPort? = null

    /**
     * 현재 연결 상태
     *
     * [상태 전이]
     * - false → connect() 성공 → true
     * - true → disconnect() 또는 오류 → false
     *
     * [스레드 안전성]
     * - @Volatile: 다른 스레드에서도 최신값 확인 가능
     * - 하지만 복합 연산(check-then-act)은 동기화 필요
     */
    @Volatile
    private var isConnected: Boolean = false

    // ============================================================
    // 공개 메서드
    // ============================================================

    /**
     * 연결된 USB-Serial 장치 목록 조회
     *
     * [동작 방식]
     * 1. UsbManager에서 모든 USB 장치 조회
     * 2. UsbSerialProber로 각 장치에 맞는 드라이버 탐색
     * 3. 드라이버가 있는(= 지원되는) 장치만 반환
     *
     * [지원 칩셋]
     * - FTDI FT232R, FT232H, FT2232
     * - Prolific PL2303
     * - Silicon Labs CP210x
     * - WCH CH340, CH341
     * - 기타 usb-serial-for-android가 지원하는 칩셋
     *
     * @return 사용 가능한 USB-Serial 드라이버 목록 (빈 리스트일 수 있음)
     */
    fun findDevices(): List<UsbSerialDriver> {
        log("USB-Serial 장치 탐색 시작...")

        // 기본 프로버로 모든 지원 칩셋 탐색
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        log("발견된 장치: ${availableDrivers.size}개")

        availableDrivers.forEachIndexed { index, serialDriver ->
            val device = serialDriver.device
            log("  [${index + 1}] ${device.productName ?: "알 수 없음"}")
            log("      Vendor ID: 0x${device.vendorId.toString(16).uppercase()}")
            log("      Product ID: 0x${device.productId.toString(16).uppercase()}")
            log("      포트 수: ${serialDriver.ports.size}")
        }

        return availableDrivers
    }

    /**
     * 발견된 첫 번째 장치에 자동 연결
     *
     * [사용 상황]
     * - USB-Serial 어댑터가 1개만 연결된 경우
     * - 빠른 테스트 및 MVP 개발 시
     *
     * [동작 방식]
     * 1. findDevices()로 장치 탐색
     * 2. 첫 번째 장치의 첫 번째 포트에 연결
     *
     * [주의]
     * - 여러 장치가 연결된 경우 어떤 것이 선택될지 불확실
     * - 특정 장치 연결이 필요하면 connect(UsbDevice) 사용
     *
     * @return 연결 성공 여부
     */
    fun connectFirst(): Boolean {
        val drivers = findDevices()

        if (drivers.isEmpty()) {
            listener.onError("USB-Serial 장치를 찾을 수 없습니다")
            return false
        }

        return connect(drivers.first())
    }

    /**
     * 특정 드라이버(장치)에 연결
     *
     * [연결 과정]
     * 1. USB 장치 연결 열기 (시스템 레벨)
     * 2. 드라이버의 첫 번째 포트 가져오기
     * 3. 포트 열기
     * 4. 통신 파라미터 설정 (Baud rate 등)
     *
     * [권한 문제]
     * - USB 권한이 없으면 openDevice()가 null 반환
     * - 이 경우 UsbPermissionHelper로 권한 요청 필요
     *
     * @param selectedDriver 연결할 드라이버
     * @return 연결 성공 여부
     */
    fun connect(selectedDriver: UsbSerialDriver): Boolean {
        // 이미 연결된 경우 먼저 해제
        if (isConnected) {
            log("기존 연결 해제 중...")
            disconnect()
        }

        driver = selectedDriver
        val device = selectedDriver.device

        log("장치 연결 시도: ${device.productName ?: device.deviceName}")

        // 1. USB 장치 연결 열기
        // 권한이 없으면 null 반환
        connection = usbManager.openDevice(device)

        if (connection == null) {
            val errorMsg = "USB 장치 열기 실패 - 권한이 없거나 장치가 사용 중"
            log(errorMsg)
            listener.onError(errorMsg)
            return false
        }

        // 2. 첫 번째 포트 가져오기
        // 대부분의 USB-Serial 어댑터는 포트 1개만 있음
        port = selectedDriver.ports.firstOrNull()

        if (port == null) {
            val errorMsg = "사용 가능한 포트가 없습니다"
            log(errorMsg)
            listener.onError(errorMsg)
            connection?.close()
            connection = null
            return false
        }

        try {
            // 3. 포트 열기
            port!!.open(connection)

            // 4. 통신 파라미터 설정
            // Constants.kt에서 정의한 값 사용
            port!!.setParameters(
                SerialConfig.BAUD_RATE,
                SerialConfig.DATA_BITS,
                SerialConfig.STOP_BITS,
                SerialConfig.PARITY
            )

            // DTR(Data Terminal Ready), RTS(Request To Send) 신호 설정
            // 일부 장비는 이 신호가 있어야 통신 시작
            port!!.dtr = true
            port!!.rts = true

            isConnected = true
            val deviceName = device.productName ?: device.deviceName
            log("연결 성공: $deviceName")
            log("설정: ${SerialConfig.BAUD_RATE} bps, ${SerialConfig.DATA_BITS}-${getParityString()}-${getStopBitsString()}")

            listener.onConnected(deviceName)
            return true

        } catch (e: IOException) {
            val errorMsg = "포트 열기 실패: ${e.message}"
            log(errorMsg)
            listener.onError(errorMsg)
            cleanup()
            return false
        }
    }

    /**
     * 특정 UsbDevice에 연결 (USB 연결 인텐트에서 받은 장치용)
     *
     * [사용 상황]
     * - USB_DEVICE_ATTACHED 인텐트로 받은 UsbDevice 객체로 연결
     * - UsbPermissionHelper에서 권한 획득 후 연결
     *
     * @param device 연결할 USB 장치
     * @return 연결 성공 여부
     */
    fun connect(device: UsbDevice): Boolean {
        // 해당 장치에 맞는 드라이버 찾기
        val prober = UsbSerialProber.getDefaultProber()
        val matchedDriver = prober.probeDevice(device)

        if (matchedDriver == null) {
            val errorMsg = "지원되지 않는 USB-Serial 장치입니다"
            log(errorMsg)
            listener.onError(errorMsg)
            return false
        }

        return connect(matchedDriver)
    }

    /**
     * 연결 해제
     *
     * [호출 시점]
     * - Activity/Fragment의 onDestroy()
     * - 사용자가 명시적으로 연결 해제 요청
     * - 오류 발생으로 인한 정리
     *
     * [동작]
     * 1. 포트 닫기
     * 2. USB 연결 닫기
     * 3. 상태 초기화
     * 4. 리스너에 알림
     *
     * [주의]
     * - 이미 해제된 상태에서 호출해도 안전 (멱등성)
     */
    fun disconnect() {
        if (!isConnected && port == null && connection == null) {
            log("이미 연결 해제된 상태")
            return
        }

        log("연결 해제 중...")
        cleanup()
        listener.onDisconnected("정상 종료")
    }

    /**
     * 배출기 트리거 명령 전송
     *
     * [동작]
     * 1. 연결 상태 확인
     * 2. Constants.kt에서 정의한 명령 바이트 가져오기
     * 3. 포트로 전송
     * 4. 결과를 리스너에 알림
     *
     * [명령 형식]
     * - TriggerCommand.USE_STRING_COMMAND = true:
     *   "TRIGGER\r\n"을 ASCII 바이트로 변환하여 전송
     * - TriggerCommand.USE_STRING_COMMAND = false:
     *   TriggerCommand.TRIGGER_COMMAND_BYTES를 그대로 전송
     *
     * @return 전송 성공 여부
     */
    fun sendTriggerCommand(): Boolean {
        if (!isConnected || port == null) {
            val errorMsg = "연결되지 않은 상태에서 명령 전송 시도"
            log(errorMsg)
            listener.onError(errorMsg)
            return false
        }

        val command = TriggerCommand.getCommand()
        return sendData(command)
    }

    /**
     * 임의의 데이터 전송
     *
     * [사용 상황]
     * - 트리거 외의 다른 명령 전송 시
     * - 디버깅 및 테스트 시
     * - 양방향 통신 구현 시
     *
     * @param data 전송할 바이트 배열
     * @return 전송 성공 여부
     */
    fun sendData(data: ByteArray): Boolean {
        if (!isConnected || port == null) {
            listener.onError("포트가 열려있지 않습니다")
            return false
        }

        return try {
            // write()는 void 반환 - 타임아웃 내에 전송 완료되지 않으면 예외 발생
            port!!.write(data, UsbConfig.WRITE_TIMEOUT_MS)
            val bytesSent = data.size

            log("전송 완료: $bytesSent 바이트")
            log("전송 데이터: ${data.toHexString()}")

            listener.onDataSent(bytesSent)
            true

        } catch (e: IOException) {
            val errorMsg = "데이터 전송 실패: ${e.message}"
            log(errorMsg)
            listener.onError(errorMsg)

            // 전송 실패 시 연결 상태 확인 필요
            // 케이블 분리 등으로 연결이 끊겼을 수 있음
            checkConnection()
            false
        }
    }

    /**
     * 현재 연결 상태 반환
     *
     * @return 연결됨 여부
     */
    fun isConnected(): Boolean = isConnected

    /**
     * 현재 연결된 장치 정보 반환
     *
     * @return 장치 이름 또는 null
     */
    fun getConnectedDeviceName(): String? {
        return driver?.device?.productName ?: driver?.device?.deviceName
    }

    /**
     * USB 권한이 있는지 확인
     *
     * @param device 확인할 장치
     * @return 권한 있음 여부
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    // ============================================================
    // 내부 헬퍼 메서드
    // ============================================================

    /**
     * 리소스 정리 (내부용)
     *
     * [정리 순서]
     * 1. 포트 닫기 (데이터 통신 중단)
     * 2. USB 연결 닫기 (시스템 리소스 해제)
     * 3. 참조 null로 설정 (가비지 컬렉션 허용)
     * 4. 상태 플래그 업데이트
     */
    private fun cleanup() {
        try {
            port?.close()
        } catch (e: IOException) {
            Log.w(LogConfig.TAG, "포트 닫기 중 오류: ${e.message}")
        }

        try {
            connection?.close()
        } catch (e: Exception) {
            Log.w(LogConfig.TAG, "연결 닫기 중 오류: ${e.message}")
        }

        port = null
        connection = null
        driver = null
        isConnected = false
    }

    /**
     * 연결 상태 확인 및 자동 복구 시도
     *
     * [호출 시점]
     * - 데이터 전송/수신 실패 후
     * - 주기적인 헬스 체크 시
     *
     * [동작]
     * - 포트가 열려있지 않으면 연결 해제 처리
     */
    private fun checkConnection() {
        if (port == null || connection == null) {
            log("연결이 끊어진 것으로 감지됨")
            isConnected = false
            cleanup()
            listener.onDisconnected("연결 끊김 감지")
        }
    }

    /**
     * 로그 메시지 출력
     *
     * [출력 대상]
     * 1. Android Logcat (개발자용)
     * 2. 리스너 콜백 (UI 표시용)
     *
     * @param message 로그 메시지
     */
    private fun log(message: String) {
        Log.d(LogConfig.TAG, message)
        listener.onLog(message)
    }

    /**
     * 패리티 설정을 사람이 읽을 수 있는 문자열로 변환
     */
    private fun getParityString(): String {
        return when (SerialConfig.PARITY) {
            UsbSerialPort.PARITY_NONE -> "N"
            UsbSerialPort.PARITY_ODD -> "O"
            UsbSerialPort.PARITY_EVEN -> "E"
            UsbSerialPort.PARITY_MARK -> "M"
            UsbSerialPort.PARITY_SPACE -> "S"
            else -> "?"
        }
    }

    /**
     * 정지 비트 설정을 사람이 읽을 수 있는 문자열로 변환
     */
    private fun getStopBitsString(): String {
        return when (SerialConfig.STOP_BITS) {
            UsbSerialPort.STOPBITS_1 -> "1"
            UsbSerialPort.STOPBITS_1_5 -> "1.5"
            UsbSerialPort.STOPBITS_2 -> "2"
            else -> "?"
        }
    }

    /**
     * 바이트 배열을 16진수 문자열로 변환 (디버깅용)
     *
     * [출력 형식]
     * - 각 바이트를 2자리 16진수로 표현
     * - 공백으로 구분
     * - 예: "02 31 03" (STX, '1', ETX)
     */
    private fun ByteArray.toHexString(): String {
        return joinToString(" ") { byte ->
            String.format("%02X", byte)
        }
    }
}
