package com.videosync.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.videosync.app.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

// 独立的 DataStore 实例用于已处理文件记录
private val Context.processedDataStore: DataStore<Preferences> by preferencesDataStore(name = "processed_files")

/**
 * 已处理文件记录管理器
 * 记录已同步的文件，避免二次处理
 */
class ProcessedFilesManager(private val context: Context) {

    companion object {
        private val KEY_PROCESSED_FILES = stringPreferencesKey("processed_files_json")
        private const val MAX_RECORDS = 5000 // 最大记录数，防止无限增长
    }

    /**
     * 已处理文件记录
     * @param originalName 原始文件名（不含扩展名）
     * @param originalExtension 原始文件扩展名
     * @param originalPath 原始文件路径
     * @param newFileName 新文件名（含扩展名）
     * @param processedAt 处理时间戳
     */
    data class ProcessedFile(
        val originalName: String,
        val originalExtension: String,
        val originalPath: String,
        val newFileName: String,
        val processedAt: Long = System.currentTimeMillis()
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("originalName", originalName)
                put("originalExtension", originalExtension)
                put("originalPath", originalPath)
                put("newFileName", newFileName)
                put("processedAt", processedAt)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): ProcessedFile {
                return ProcessedFile(
                    originalName = json.getString("originalName"),
                    originalExtension = json.getString("originalExtension"),
                    originalPath = json.getString("originalPath"),
                    newFileName = json.getString("newFileName"),
                    processedAt = json.getLong("processedAt")
                )
            }
        }
    }

    /**
     * 获取所有已处理文件的 Flow
     */
    val processedFilesFlow: Flow<List<ProcessedFile>> = context.processedDataStore.data.map { preferences ->
        val jsonStr = preferences[KEY_PROCESSED_FILES] ?: "[]"
        parseJsonArray(jsonStr)
    }

    /**
     * 检查文件是否已处理
     * @param fileName 文件基础名（不含扩展名）
     * @param filePath 文件路径
     * @return true 表示已处理
     */
    suspend fun isProcessed(fileName: String, filePath: String): Boolean {
        val files = context.processedDataStore.data.first()[KEY_PROCESSED_FILES] ?: "[]"
        val processedList = parseJsonArray(files)
        return processedList.any { 
            it.originalName == fileName || it.originalPath == filePath
        }
    }

    /**
     * 记录已处理的文件
     */
    suspend fun markAsProcessed(
        originalName: String,
        originalExtension: String,
        originalPath: String,
        newFileName: String
    ) {
        context.processedDataStore.edit { preferences ->
            val jsonStr = preferences[KEY_PROCESSED_FILES] ?: "[]"
            val currentList = parseJsonArray(jsonStr).toMutableList()

            // 添加新记录
            currentList.add(
                ProcessedFile(
                    originalName = originalName,
                    originalExtension = originalExtension,
                    originalPath = originalPath,
                    newFileName = newFileName
                )
            )

            // 限制记录数量，移除最旧的记录
            while (currentList.size > MAX_RECORDS) {
                currentList.removeAt(0)
            }

            // 保存回 DataStore
            preferences[KEY_PROCESSED_FILES] = toJsonArray(currentList)

            Logger.d("ProcessedFiles", "记录已处理文件: $originalName -> $newFileName")
        }
    }

    /**
     * 获取已处理文件数量
     */
    suspend fun getProcessedCount(): Int {
        val files = context.processedDataStore.data.first()[KEY_PROCESSED_FILES] ?: "[]"
        return parseJsonArray(files).size
    }

    /**
     * 清除所有已处理记录
     */
    suspend fun clearAll() {
        context.processedDataStore.edit { preferences ->
            preferences[KEY_PROCESSED_FILES] = "[]"
        }
        Logger.i("ProcessedFiles", "已清除所有处理记录")
    }

    /**
     * 解析 JSON 数组字符串
     */
    private fun parseJsonArray(jsonStr: String): List<ProcessedFile> {
        return try {
            val jsonArray = JSONArray(jsonStr)
            (0 until jsonArray.length()).mapNotNull { i ->
                try {
                    ProcessedFile.fromJson(jsonArray.getJSONObject(i))
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 将列表转换为 JSON 数组字符串
     */
    private fun toJsonArray(files: List<ProcessedFile>): String {
        val jsonArray = JSONArray()
        files.forEach { jsonArray.put(it.toJson()) }
        return jsonArray.toString()
    }
}
