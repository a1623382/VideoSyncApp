package com.videosync.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 电池优化管理器
 * 检查并引导用户关闭电池优化，确保后台同步任务不被系统杀死
 */
object BatteryOptimizationHelper {

    /**
     * 检查应用是否已忽略电池优化
     * @param context 应用上下文
     * @return true 表示已忽略（可以后台运行），false 表示未忽略
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    /**
     * 获取请求忽略电池优化的 Intent
     * @param context 应用上下文
     * @return Intent 用于跳转到电池优化设置页面
     */
    fun getRequestIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            // Android 6.0 以下不需要电池优化
            Intent()
        }
    }

    /**
     * 获取电池优化设置页面的 Intent
     * 用于引导用户手动关闭电池优化
     */
    fun getBatteryOptimizationSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * 检查并请求电池优化忽略权限
     * @param context 应用上下文
     * @return true 表示已忽略，false 表示需要用户操作
     */
    fun checkAndRequest(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) {
            return true
        }
        return false
    }
}
