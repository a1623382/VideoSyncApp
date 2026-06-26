package com.videosync.app

import android.app.Application
import com.videosync.app.util.Logger

class VideoSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化日志系统
        Logger.init(this)
        Logger.i("VideoSyncApplication", "应用启动")
    }
}
