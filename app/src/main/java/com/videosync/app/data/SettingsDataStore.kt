package com.videosync.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.videosync.app.util.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore 实例扩展
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nas_settings")

/**
 * NAS 连接配置数据类
 * @param host NAS 服务器 IP 地址
 * @param port SMB 端口号
 * @param shareName 共享文件夹名称（SMB share）
 * @param username 用户名
 * @param password 密码（明文，仅在内存中使用）
 * @param remotePath 远端子目录路径（共享文件夹下的相对路径）
 */
data class NasConfig(
    val host: String = "",
    val port: Int = 445,
    val shareName: String = "",
    val username: String = "",
    val password: String = "",
    val remotePath: String = ""
)

/**
 * DataStore 配置管理器
 * 使用加密存储保护用户凭据，支持自动读取和填充
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        // DataStore 偏好键定义
        private val KEY_NAS_HOST = stringPreferencesKey("nas_host")
        private val KEY_NAS_PORT = intPreferencesKey("nas_port")
        private val KEY_SHARE_NAME = stringPreferencesKey("share_name")
        private val KEY_NAS_USERNAME = stringPreferencesKey("nas_username")
        private val KEY_NAS_PASSWORD_ENCRYPTED = stringPreferencesKey("nas_password_encrypted")
        private val KEY_REMOTE_PATH = stringPreferencesKey("remote_path")
    }

    /**
     * 获取 NAS 配置的 Flow 流
     * 每次配置变更时自动发射新值，用于 UI 自动填充
     */
    val nasConfigFlow: Flow<NasConfig> = context.dataStore.data.map { preferences ->
        NasConfig(
            host = preferences[KEY_NAS_HOST] ?: "",
            port = preferences[KEY_NAS_PORT] ?: 445,
            shareName = preferences[KEY_SHARE_NAME] ?: "",
            username = preferences[KEY_NAS_USERNAME] ?: "",
            password = decryptPassword(preferences[KEY_NAS_PASSWORD_ENCRYPTED] ?: ""),
            remotePath = preferences[KEY_REMOTE_PATH] ?: ""
        )
    }

    /**
     * 保存 NAS 配置到 DataStore
     * 密码字段使用 Android KeyStore 加密后存储
     */
    suspend fun saveNasConfig(config: NasConfig) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NAS_HOST] = config.host
            preferences[KEY_NAS_PORT] = config.port
            preferences[KEY_SHARE_NAME] = config.shareName
            preferences[KEY_NAS_USERNAME] = config.username
            // 密码加密后存储
            preferences[KEY_NAS_PASSWORD_ENCRYPTED] = CryptoManager.encrypt(config.password)
            preferences[KEY_REMOTE_PATH] = config.remotePath
        }
    }

    /**
     * 解密密码字段
     */
    private fun decryptPassword(encrypted: String): String {
        return try {
            CryptoManager.decrypt(encrypted)
        } catch (e: Exception) {
            // 解密失败返回空字符串
            ""
        }
    }
}
