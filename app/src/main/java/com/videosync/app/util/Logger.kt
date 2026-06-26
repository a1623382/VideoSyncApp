package com.videosync.app.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 日志管理器
 * 支持多级别日志记录、文件持久化和日志导出
 * 用于离线调试，无需连接手机即可分析问题
 */
object Logger {

    /**
     * 日志级别枚举
     */
    enum class Level(val tag: String) {
        DEBUG("D"),
        INFO("I"),
        WARNING("W"),
        ERROR("E")
    }

    /**
     * 日志条目数据类
     */
    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

        /**
         * 格式化为可读字符串
         */
        fun format(): String {
            val time = dateFormat.format(Date(timestamp))
            val sb = StringBuilder("[$time] ${level.tag}/$tag: $message")
            throwable?.let { t ->
                val sw = StringWriter()
                t.printStackTrace(PrintWriter(sw))
                sb.append("\n$sw")
            }
            return sb.toString()
        }
    }

    // 内存中的日志缓存（线程安全）
    private val logBuffer = CopyOnWriteArrayList<LogEntry>()

    // 日志文件目录
    private var logDir: File? = null

    // 最大日志文件大小 (5MB)
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024L

    // 最大内存日志条数
    private const val MAX_BUFFER_SIZE = 10000

    /**
     * 初始化日志系统
     * @param context 应用上下文
     */
    fun init(context: Context) {
        logDir = File(context.getExternalFilesDir(null), "logs").apply {
            if (!exists()) mkdirs()
        }
        i("Logger", "日志系统初始化完成，日志目录: ${logDir?.absolutePath}")
    }

    /**
     * 记录 DEBUG 级别日志
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.DEBUG, tag, message, throwable)
    }

    /**
     * 记录 INFO 级别日志
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.INFO, tag, message, throwable)
    }

    /**
     * 记录 WARNING 级别日志
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARNING, tag, message, throwable)
    }

    /**
     * 记录 ERROR 级别日志
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }

    /**
     * 核心日志记录方法
     */
    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        val entry = LogEntry(level = level, tag = tag, message = message, throwable = throwable)

        // 添加到内存缓冲区
        logBuffer.add(entry)

        // 保持缓冲区大小
        while (logBuffer.size > MAX_BUFFER_SIZE) {
            logBuffer.removeAt(0)
        }

        // 同时输出到 Android Logcat
        when (level) {
            Level.DEBUG -> android.util.Log.d(tag, message, throwable)
            Level.INFO -> android.util.Log.i(tag, message, throwable)
            Level.WARNING -> android.util.Log.w(tag, message, throwable)
            Level.ERROR -> android.util.Log.e(tag, message, throwable)
        }

        // 异步写入文件
        writeToFile(entry)
    }

    /**
     * 将日志写入文件
     */
    private fun writeToFile(entry: LogEntry) {
        try {
            val dir = logDir ?: return
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val logFile = File(dir, "videosync_$dateStr.log")

            // 检查文件大小，超过限制则创建新文件
            val targetFile = if (logFile.exists() && logFile.length() > MAX_FILE_SIZE) {
                val timestamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
                File(dir, "videosync_${dateStr}_$timestamp.log")
            } else {
                logFile
            }

            FileWriter(targetFile, true).use { writer ->
                writer.appendLine(entry.format())
            }
        } catch (e: Exception) {
            // 日志写入失败不应影响主程序
            android.util.Log.e("Logger", "写入日志文件失败", e)
        }
    }

    /**
     * 获取所有内存中的日志
     * @return 日志条目列表
     */
    fun getLogs(): List<LogEntry> = logBuffer.toList()

    /**
     * 按级别过滤日志
     * @param level 日志级别
     * @return 过滤后的日志列表
     */
    fun getLogsByLevel(level: Level): List<LogEntry> {
        return logBuffer.filter { it.level == level }
    }

    /**
     * 按标签过滤日志
     * @param tag 标签名
     * @return 过滤后的日志列表
     */
    fun getLogsByTag(tag: String): List<LogEntry> {
        return logBuffer.filter { it.tag == tag }
    }

    /**
     * 获取最近的 N 条日志
     * @param count 日志条数
     * @return 最近日志列表
     */
    fun getRecentLogs(count: Int = 100): List<LogEntry> {
        return logBuffer.takeLast(count)
    }

    /**
     * 导出日志到文件并返回文件路径
     * @param context 应用上下文
     * @return 导出的日志文件路径，失败返回 null
     */
    suspend fun exportLogs(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "VideoSync"
            ).apply {
                if (!exists()) mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFile = File(exportDir, "videosync_log_$timestamp.txt")

            FileWriter(exportFile).use { writer ->
                // 写入头部信息
                writer.appendLine("=".repeat(60))
                writer.appendLine("视频同步助手 - 日志导出")
                writer.appendLine("导出时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                writer.appendLine("日志条数: ${logBuffer.size}")
                writer.appendLine("=".repeat(60))
                writer.appendLine()

                // 写入内存中的日志
                writer.appendLine("--- 内存日志 ---")
                for (entry in logBuffer) {
                    writer.appendLine(entry.format())
                }
                writer.appendLine()

                // 写入文件中的日志
                logDir?.let { dir ->
                    val logFiles = dir.listFiles()?.sortedByDescending { it.lastModified() }
                    if (logFiles != null) {
                        writer.appendLine("--- 历史日志文件 ---")
                        for (file in logFiles.take(5)) { // 最多导出5个历史文件
                            writer.appendLine("\n[文件: ${file.name}]")
                            file.forEachLine { line ->
                                writer.appendLine(line)
                            }
                        }
                    }
                }
            }

            exportFile
        } catch (e: Exception) {
            e("Logger", "导出日志失败", e)
            null
        }
    }

    /**
     * 创建分享日志的 Intent
     * @param context 应用上下文
     * @param logFile 日志文件
     * @return 分享 Intent
     */
    fun createShareIntent(context: Context, logFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "视频同步助手日志")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 清除内存中的日志
     */
    fun clearMemoryLogs() {
        logBuffer.clear()
        i("Logger", "内存日志已清除")
    }

    /**
     * 清除所有日志文件
     */
    suspend fun clearAllLogs() = withContext(Dispatchers.IO) {
        logDir?.listFiles()?.forEach { it.delete() }
        clearMemoryLogs()
        i("Logger", "所有日志已清除")
    }

    /**
     * 获取日志统计信息
     */
    fun getStats(): String {
        val debugCount = logBuffer.count { it.level == Level.DEBUG }
        val infoCount = logBuffer.count { it.level == Level.INFO }
        val warningCount = logBuffer.count { it.level == Level.WARNING }
        val errorCount = logBuffer.count { it.level == Level.ERROR }

        return buildString {
            appendLine("日志统计:")
            appendLine("  DEBUG: $debugCount")
            appendLine("  INFO: $infoCount")
            appendLine("  WARNING: $warningCount")
            appendLine("  ERROR: $errorCount")
            appendLine("  总计: ${logBuffer.size}")
        }
    }
}
