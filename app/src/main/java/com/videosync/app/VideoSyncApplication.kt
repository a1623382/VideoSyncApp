package com.videosync.app

import android.app.Application
import com.videosync.app.util.Logger
import com.videosync.app.util.NotificationHelper

class VideoSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化日志系统
        Logger.init(this)
        // 初始化通知渠道
        NotificationHelper.createNotificationChannel(this)
        Logger.i("VideoSyncApplication", "应用启动")
    }
}
