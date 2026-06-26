package com.videosync.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.videosync.app.MainActivity

/**
 * 同步前台服务
 * 用于保持应用在后台运行，防止系统杀死同步任务
 * 显示常驻通知和实时进度
 */
class SyncForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "sync_foreground_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_UPDATE_PROGRESS = "com.videosync.app.UPDATE_PROGRESS"
        const val ACTION_STOP_SERVICE = "com.videosync.app.STOP_SERVICE"

        // Intent extras
        const val EXTRA_CURRENT_FILE = "current_file"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_CURRENT_INDEX = "current_index"
        const val EXTRA_TOTAL_COUNT = "total_count"
        const val EXTRA_IS_PAUSED = "is_paused"
        const val EXTRA_IS_COMPLETED = "is_completed"
        const val EXTRA_SUCCESS_COUNT = "success_count"
        const val EXTRA_FAILED_COUNT = "failed_count"

        /**
         * 启动前台服务
         */
        fun start(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止前台服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            context.stopService(intent)
        }

        /**
         * 更新同步进度
         */
        fun updateProgress(
            context: Context,
            currentFile: String,
            progress: Float,
            currentIndex: Int,
            totalCount: Int,
            isPaused: Boolean = false
        ) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_CURRENT_FILE, currentFile)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_CURRENT_INDEX, currentIndex)
                putExtra(EXTRA_TOTAL_COUNT, totalCount)
                putExtra(EXTRA_IS_PAUSED, isPaused)
            }
            context.startService(intent)
        }
    }

    private var currentFile = ""
    private var progress = 0f
    private var currentIndex = 0
    private var totalCount = 0
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_PROGRESS -> {
                currentFile = intent.getStringExtra(EXTRA_CURRENT_FILE) ?: ""
                progress = intent.getFloatExtra(EXTRA_PROGRESS, 0f)
                currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)
                totalCount = intent.getIntExtra(EXTRA_TOTAL_COUNT, 0)
                isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false)
                updateNotification()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "视频同步服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持同步任务在后台运行"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // 点击通知打开应用
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 停止同步的 Intent
        val stopIntent = Intent(this, SyncForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            isPaused -> "已暂停"
            progress > 0f -> "同步中"
            else -> "准备中"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("视频同步 $statusText")
            .setContentText("点击查看详情")
            .setSubText("$currentIndex/$totalCount")
            .setProgress(100, progress.toInt(), false)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }
}
