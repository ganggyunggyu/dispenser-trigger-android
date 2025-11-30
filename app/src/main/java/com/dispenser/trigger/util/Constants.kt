/**
 * Constants.kt
 *
 * 앱 전체에서 사용하는 상수값을 한 곳에서 관리하는 파일.
 *
 * [왜 상수를 분리하는가]
 * 1. 유지보수 용이: 설정값 변경 시 이 파일만 수정
 * 2. 가독성: 매직 넘버 대신 의미 있는 이름 사용
 * 3. 일관성: 같은 값을 여러 곳에서 사용할 때 불일치 방지
 * 4. 문서화: 각 상수의 역할과 수정 시 주의사항을 명시
 *
 * [구조]
 * - SerialConfig: RS-232 통신 설정값
 * - TriggerCommand: 배출기 트리거 명령 관련
 * - UsbConfig: USB 권한 및 타임아웃 설정
 * - LogConfig: 로그 관련 설정
 *
 * [수정 시 참고]
 * - 배출기 장비 프로토콜이 변경되면 TriggerCommand 수정
 * - 통신 속도 변경 시 SerialConfig.BAUD_RATE 수정
 * - 새로운 설정 카테고리 추가 시 object로 그룹화
 */
package com.dispenser.trigger.util

import com.hoho.android.usbserial.driver.UsbSerialPort

/**
 * RS-232 시리얼 통신 설정값
 *
 * [왜 이 값들인가]
 * - 대부분의 산업용 RS-232 장비는 9600-8-N-1 설정을 기본으로 사용
 * - 9600 bps: 저속이지만 노이즈에 강하고 케이블 길이 제한이 적음
 * - 8 data bits: 표준 ASCII 문자 전송에 적합
 * - No parity: 간단한 명령 전송에는 패리티 검사 불필요
 * - 1 stop bit: 일반적인 설정
 *
 * [수정 시 참고]
 * - 배출기 매뉴얼에서 통신 설정 확인 후 변경
 * - BAUD_RATE 변경 시 가능한 값: 300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200
 * - DATA_BITS 변경 시 가능한 값: DATABITS_5, DATABITS_6, DATABITS_7, DATABITS_8
 * - PARITY 변경 시 가능한 값: PARITY_NONE, PARITY_ODD, PARITY_EVEN, PARITY_MARK, PARITY_SPACE
 * - STOP_BITS 변경 시 가능한 값: STOPBITS_1, STOPBITS_1_5, STOPBITS_2
 */
object SerialConfig {
    /**
     * 보 레이트 (Baud Rate) - 초당 전송 비트 수
     *
     * [일반적인 값]
     * - 9600: 대부분의 구형 장비, 안정성 우선
     * - 19200: 중간 속도
     * - 115200: 고속 통신, 최신 장비
     *
     * [선택 기준]
     * - 배출기 매뉴얼 확인 필수
     * - 케이블이 길면(10m+) 낮은 속도 권장
     * - 노이즈가 심한 환경에서는 낮은 속도가 안정적
     */
    const val BAUD_RATE: Int = 9600

    /**
     * 데이터 비트 수
     *
     * [의미]
     * - 한 번에 전송하는 데이터의 비트 수
     * - 8비트가 표준 (ASCII 문자 1개 = 8비트)
     *
     * [가능한 값]
     * - DATABITS_5: 특수 장비용
     * - DATABITS_6: 특수 장비용
     * - DATABITS_7: 구형 ASCII 장비
     * - DATABITS_8: 표준 (권장)
     */
    const val DATA_BITS: Int = UsbSerialPort.DATABITS_8

    /**
     * 정지 비트 수
     *
     * [의미]
     * - 각 바이트 전송 후 라인을 유휴 상태로 유지하는 시간
     * - 수신측이 다음 바이트를 준비할 시간 제공
     *
     * [가능한 값]
     * - STOPBITS_1: 표준 (권장)
     * - STOPBITS_1_5: 5 데이터 비트 사용 시
     * - STOPBITS_2: 느린 수신 장치용
     */
    const val STOP_BITS: Int = UsbSerialPort.STOPBITS_1

    /**
     * 패리티 비트 설정
     *
     * [의미]
     * - 전송 오류 검출을 위한 추가 비트
     * - 간단한 명령 전송에는 보통 사용 안 함
     *
     * [가능한 값]
     * - PARITY_NONE: 패리티 없음 (권장, 단순 통신)
     * - PARITY_ODD: 홀수 패리티
     * - PARITY_EVEN: 짝수 패리티
     * - PARITY_MARK: 항상 1
     * - PARITY_SPACE: 항상 0
     */
    const val PARITY: Int = UsbSerialPort.PARITY_NONE
}

/**
 * 배출기 트리거 명령 설정
 *
 * [배출기 통신 프로토콜]
 * - 대부분의 산업용 배출기는 간단한 텍스트 명령 사용
 * - 명령 전송 → 배출기 동작 → (선택적) 응답 수신
 *
 * [일반적인 프로토콜 형식]
 * 1. 단순 텍스트: "TRIGGER\r\n"
 * 2. STX-ETX 프레임: [0x02][데이터][0x03]
 * 3. 체크섬 포함: [0x02][데이터][체크섬][0x03]
 *
 * [수정 시 참고]
 * - 배출기 매뉴얼에서 정확한 명령 포맷 확인
 * - 바이너리 명령은 TRIGGER_COMMAND_BYTES 사용
 * - 텍스트 명령은 TRIGGER_COMMAND_STRING 사용
 */
object TriggerCommand {
    /**
     * 텍스트 형식 트리거 명령
     *
     * [형식]
     * - "TRIGGER": 명령어
     * - "\r\n": 캐리지 리턴 + 줄 바꿈 (Windows 스타일 줄 끝)
     *
     * [왜 \r\n 인가]
     * - 대부분의 산업용 장비는 줄 끝 문자로 명령 종료 인식
     * - \r\n (CRLF)이 가장 널리 호환됨
     * - 일부 장비는 \r 또는 \n만 사용할 수도 있음
     *
     * [수정 예시]
     * - "DISPENSE\r\n": 다른 명령어 사용
     * - "1\r\n": 단순 숫자 명령
     * - "\r": 줄 바꿈 없이 캐리지 리턴만
     */
    const val TRIGGER_COMMAND_STRING: String = "TRIGGER\r\n"

    /**
     * 바이너리 형식 트리거 명령
     *
     * [형식]
     * - 0x02 (STX): Start of Text - 메시지 시작
     * - 0x31 ('1'): 명령 데이터
     * - 0x03 (ETX): End of Text - 메시지 끝
     *
     * [사용 상황]
     * - 텍스트 명령이 아닌 바이너리 프로토콜 사용 시
     * - 체크섬이나 특수 제어 문자가 필요한 경우
     *
     * [수정 예시]
     * - byteArrayOf(0x02, 0x44, 0x49, 0x53, 0x50, 0x03): "DISP" with STX/ETX
     * - byteArrayOf(0x01, 0x02, 0x03): 커스텀 프로토콜
     */
    val TRIGGER_COMMAND_BYTES: ByteArray = byteArrayOf(0x02, 0x31, 0x03)

    /**
     * 현재 사용할 명령 형식 선택
     *
     * [true]
     * - TRIGGER_COMMAND_STRING을 바이트로 변환하여 전송
     * - 텍스트 기반 프로토콜에 적합
     *
     * [false]
     * - TRIGGER_COMMAND_BYTES를 그대로 전송
     * - 바이너리 프로토콜에 적합
     *
     * [수정 방법]
     * - 배출기 프로토콜에 따라 true/false 선택
     */
    const val USE_STRING_COMMAND: Boolean = true

    /**
     * 실제 전송할 명령 바이트 배열 반환
     *
     * [왜 함수로 만들었나]
     * - USE_STRING_COMMAND에 따라 적절한 명령 자동 선택
     * - 호출하는 쪽에서 조건문 작성 불필요
     *
     * @return 전송할 바이트 배열
     */
    fun getCommand(): ByteArray {
        return if (USE_STRING_COMMAND) {
            TRIGGER_COMMAND_STRING.toByteArray(Charsets.US_ASCII)
        } else {
            TRIGGER_COMMAND_BYTES
        }
    }
}

/**
 * USB 관련 설정
 *
 * [USB 통신 특성]
 * - USB는 폴링 방식으로 동작
 * - 타임아웃 설정이 중요 (무한 대기 방지)
 * - 권한 요청 시 사용자 응답 대기
 */
object UsbConfig {
    /**
     * USB 권한 요청 인텐트 액션
     *
     * [왜 필요한가]
     * - Android에서 USB 장치 접근 시 사용자 권한 필요
     * - PendingIntent로 권한 요청 결과 수신
     * - 고유한 액션 문자열로 다른 앱과 구분
     *
     * [수정 시 참고]
     * - 패키지명 변경 시 이 값도 함께 변경 권장
     * - 다른 앱과 충돌 방지를 위해 패키지명 포함
     */
    const val ACTION_USB_PERMISSION: String = "com.dispenser.trigger.USB_PERMISSION"

    /**
     * 쓰기 작업 타임아웃 (밀리초)
     *
     * [왜 1000ms인가]
     * - 대부분의 RS-232 명령은 수십 바이트 이하
     * - 9600 bps에서도 1초면 충분히 전송 가능
     * - 너무 짧으면 불안정, 너무 길면 UI 응답성 저하
     *
     * [수정 시 참고]
     * - 긴 데이터 전송 시 증가 필요
     * - 빠른 실패 감지를 원하면 감소
     * - 0으로 설정 시 무한 대기 (위험!)
     */
    const val WRITE_TIMEOUT_MS: Int = 1000

    /**
     * 읽기 작업 타임아웃 (밀리초)
     *
     * [왜 500ms인가]
     * - 배출기 응답 대기 시간
     * - 응답이 없는 경우에도 빠르게 다음 동작 가능
     *
     * [수정 시 참고]
     * - 배출기 응답 속도에 따라 조절
     * - 응답을 기다리지 않는다면 이 값은 무시됨
     */
    const val READ_TIMEOUT_MS: Int = 500

    /**
     * USB 연결 재시도 간격 (밀리초)
     *
     * [사용 상황]
     * - 연결 실패 후 자동 재시도 시
     * - 장치 분리 후 재연결 감지 시
     */
    const val RECONNECT_DELAY_MS: Long = 2000L
}

/**
 * 로그 관련 설정
 *
 * [로그의 역할]
 * - 디버깅: 문제 발생 시 원인 추적
 * - 사용자 안내: 현재 상태 표시
 * - 감사(Audit): 언제 어떤 동작이 실행되었는지 기록
 */
object LogConfig {
    /**
     * 로그 태그 (Logcat 필터용)
     *
     * [사용 방법]
     * - Logcat에서 이 태그로 필터링
     * - Log.d(LogConfig.TAG, "메시지")
     */
    const val TAG: String = "DispenserTrigger"

    /**
     * UI 로그에 표시할 최대 줄 수
     *
     * [왜 제한하는가]
     * - 무한히 쌓이면 메모리 사용량 증가
     * - 스크롤이 너무 길어지면 가독성 저하
     *
     * [수정 시 참고]
     * - 더 많은 로그가 필요하면 증가
     * - 메모리가 부족한 기기에서는 감소
     */
    const val MAX_LOG_LINES: Int = 100

    /**
     * 로그 타임스탬프 형식
     *
     * [형식 설명]
     * - HH: 24시간제 시
     * - mm: 분
     * - ss: 초
     * - SSS: 밀리초
     *
     * [예시 출력]
     * "14:30:45.123"
     */
    const val TIMESTAMP_FORMAT: String = "HH:mm:ss.SSS"
}

/**
 * UI 관련 설정
 */
object UiConfig {
    /**
     * 버튼 연속 클릭 방지 시간 (밀리초)
     *
     * [왜 필요한가]
     * - 사용자가 버튼을 빠르게 여러 번 누를 수 있음
     * - 각 클릭마다 명령이 전송되면 배출기 오동작 가능
     * - 최소 간격을 두어 중복 명령 방지
     *
     * [수정 시 참고]
     * - 배출기 처리 속도에 따라 조절
     * - 너무 길면 사용자 경험 저하
     * - 너무 짧으면 중복 명령 발생 가능
     */
    const val BUTTON_DEBOUNCE_MS: Long = 500L
}
