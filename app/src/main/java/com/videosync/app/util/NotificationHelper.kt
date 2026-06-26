package com.videosync.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.videosync.app.MainActivity
import com.videosync.app.R

/**
 * 通知管理器
 * 负责创建通知渠道和发送同步进度通知
 */
object NotificationHelper {

    private const val CHANNEL_ID = "video_sync_channel"
    private const val CHANNEL_NAME = "视频同步"
    private const val NOTIFICATION_ID = 1001

    /**
     * 创建通知渠道（Android 8.0+）
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "视频同步进度通知"
                setShowBadge(false)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 显示同步进度通知
     * @param context 应用上下文
     * @param currentFile 当前处理的文件名
     * @param progress 当前文件进度 (0-100)
     * @param currentIndex 当前文件索引
     * @param totalCount 总文件数
     * @param isPaused 是否暂停
     */
    fun showSyncProgress(
        context: Context,
        currentFile: String,
        progress: Float,
        currentIndex: Int,
        totalCount: Int,
        isPaused: Boolean = false
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        // 点击通知打开应用
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            isPaused -> "已暂停"
            progress >= 100f -> "已完成"
            else -> "同步中"
        }

        val title = "视频同步 $statusText ($currentIndex/$totalCount)"
        val content = if (currentFile.isNotEmpty()) {
            "正在处理：$currentFile"
        } else {
            "准备开始同步..."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(100, progress.toInt(), false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * 显示同步完成通知
     */
    fun showSyncComplete(
        context: Context,
        successCount: Int,
        totalCount: Int,
        failedCount: Int
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "同步完成"
        val content = buildString {
            append("成功: $successCount/$totalCount")
            if (failedCount > 0) {
                append(" | 失败: $failedCount")
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID + 1, builder.build())

        // 移除进度通知
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * 移除所有通知
     */
    fun cancelAll(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()
    }
}
