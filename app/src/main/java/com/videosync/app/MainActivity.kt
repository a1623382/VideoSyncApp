package com.videosync.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.videosync.app.ui.screens.MainScreen
import com.videosync.app.ui.theme.VideoSyncAppTheme

/**
 * 主 Activity - 应用唯一入口
 * 采用单 Activity 架构，使用 Jetpack Compose 构建 UI
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用边到边显示模式
        enableEdgeToEdge()

        setContent {
            VideoSyncAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}
