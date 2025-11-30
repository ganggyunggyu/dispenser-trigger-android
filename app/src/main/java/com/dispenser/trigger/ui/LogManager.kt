package com.dispenser.trigger.ui

import android.widget.ScrollView
import android.widget.TextView
import com.dispenser.trigger.util.LogConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 로그 표시 및 관리를 담당하는 클래스
 *
 * @param logTextView 로그를 표시할 TextView
 * @param logScrollView 로그 영역의 ScrollView
 */
class LogManager(
    private val logTextView: TextView,
    private val logScrollView: ScrollView
) {
    private var currentLogLines: Int = 0
    private val timestampFormatter = SimpleDateFormat(LogConfig.TIMESTAMP_FORMAT, Locale.getDefault())

    /**
     * 로그 메시지 추가
     */
    fun append(message: String) {
        val timestamp = timestampFormatter.format(Date())
        val logLine = "[$timestamp] $message\n"

        logTextView.append(logLine)
        currentLogLines++

        if (currentLogLines > LogConfig.MAX_LOG_LINES) {
            trimOldLogs()
        }

        scrollToBottom()
    }

    /**
     * 오래된 로그 삭제
     */
    private fun trimOldLogs() {
        val text = logTextView.text.toString()
        val lines = text.lines()

        if (lines.size > LogConfig.MAX_LOG_LINES) {
            val newText = lines.drop(LogConfig.MAX_LOG_LINES / 2).joinToString("\n")
            logTextView.text = newText
            currentLogLines = lines.size - LogConfig.MAX_LOG_LINES / 2
        }
    }

    /**
     * 로그 영역 자동 스크롤
     */
    private fun scrollToBottom() {
        logScrollView.post {
            logScrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    /**
     * 로그 초기화
     */
    fun clear() {
        logTextView.text = ""
        currentLogLines = 0
        append("로그 초기화됨")
    }
}
