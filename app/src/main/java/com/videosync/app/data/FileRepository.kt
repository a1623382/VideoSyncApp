package com.videosync.app.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.videosync.app.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * 文件仓库管理器
 * 负责本地文件扫描、权限管理、文件匹配与替换逻辑
 */
class FileRepository(private val context: Context) {

    companion object {
        // 支持扫描的视频格式列表
        val VIDEO_EXTENSIONS = setOf(
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm",
            "m4v", "mpg", "mpeg", "3gp", "ts", "vob", "rmvb", "rm"
        )

        // 远端高画质格式（用于匹配替换）
        val HQ_EXTENSIONS = setOf("mkv", "mp4")
    }

    /**
     * 本地视频文件信息
     * @param name 文件基础名（不含扩展名）
     * @param extension 文件扩展名
     * @param path 文件完整绝对路径
     * @param size 文件大小（字节）
     * @param uri 文件的 MediaStore URI
     */
    data class LocalVideoInfo(
        val name: String,
        val extension: String,
        val path: String,
        val size: Long,
        val uri: Uri?
    )

    /**
     * 同步结果数据类
     */
    data class SyncResult(
        val success: Boolean,
        val message: String,
        val fileName: String = ""
    )

    /**
     * 检查是否拥有所有文件访问权限
     * Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Android 10 及以下使用传统权限检查
            val readPermission = context.checkSelfPermission(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
            val writePermission = context.checkSelfPermission(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            readPermission == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    writePermission == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取引导用户开启所有文件访问权限的 Intent
     * 用于跳转到系统设置页面
     */
    fun getStoragePermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    /**
     * 使用 MediaStore API 扫描设备内所有视频文件
     * 支持多种视频格式，返回去重后的视频列表
     * @return 视频文件信息列表
     */
    suspend fun scanAllVideos(): List<LocalVideoInfo> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<LocalVideoInfo>()
        var scannedCount = 0
        var skippedCount = 0

        // 构建 MediaStore 查询 URI
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        // 查询所有视频文件
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()

                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                val path = cursor.getString(pathColumn) ?: continue
                val size = cursor.getLong(sizeColumn)

                // 检查文件是否仍然存在（避免匹配已删除的文件）
                val file = File(path)
                if (!file.exists()) {
                    skippedCount++
                    continue
                }

                // 提取扩展名并检查是否为支持的视频格式
                val extension = name.substringAfterLast('.', "").lowercase()
                if (extension !in VIDEO_EXTENSIONS) continue

                // 获取基础文件名（不含扩展名）
                val baseName = name.substringBeforeLast('.')

                // 构建 content URI
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                videos.add(
                    LocalVideoInfo(
                        name = baseName,
                        extension = extension,
                        path = path,
                        size = size,
                        uri = contentUri
                    )
                )
                scannedCount++
            }
        }

        Logger.d("FileRepository", "扫描完成：有效 $scannedCount 个，跳过已删除 $skippedCount 个")

        // 按基础文件名去重，保留第一个遇到的文件
        videos.distinctBy { it.name }
    }

    /**
     * 检查远端是否有匹配的高画质文件
     * 优先匹配目录结构相似的文件，避免错误匹配
     * @param localPath 本地视频完整路径
     * @param localName 本地视频基础名（不含扩展名）
     * @param localExtension 本地视频扩展名
     * @param remoteFiles 远端文件列表
     * @return 匹配的远端文件信息，null 表示无匹配
     */
    fun findMatchingRemoteFile(
        localPath: String,
        localName: String,
        localExtension: String,
        remoteFiles: List<RemoteFileInfo>
    ): RemoteFileInfo? {
        // 获取本地文件的上一级目录名
        val localParentDir = localPath.substringBeforeLast('/').substringAfterLast('/')

        // 找到所有同名且格式匹配的远端文件
        val candidates = remoteFiles.filter { remote ->
            val remoteBaseName = remote.name.substringBeforeLast('.')
            val remoteExtension = remote.name.substringAfterLast('.').lowercase()

            // 基本匹配条件：
            // 1. 基础文件名完全相同
            // 2. 远端扩展名是高画质格式
            // 3. 远端扩展名与本地不同（避免重复下载同格式文件）
            remoteBaseName == localName &&
                    remoteExtension in HQ_EXTENSIONS &&
                    remoteExtension != localExtension.lowercase()
        }

        if (candidates.isEmpty()) return null

        // 计算每个候选文件的目录相似性得分
        val scoredCandidates = candidates.map { remote ->
            val remoteParentDir = remote.path.substringBeforeLast('/').substringAfterLast('/')
            val similarity = calculateDirectorySimilarity(localParentDir, remoteParentDir)
            Pair(remote, similarity)
        }

        // 按相似性得分降序排序
        val sorted = scoredCandidates.sortedByDescending { it.second }

        // 如果最高相似性得分太低（目录差异大），且有多个候选，返回null避免错误匹配
        // 但如果只有一个候选，即使目录不同也返回（因为没有更好的选择）
        if (sorted.size > 1 && sorted[0].second < 0.3f) {
            Logger.w("FileRepository", "目录差异过大，跳过匹配: 本地=$localParentDir, 远端=${sorted[0].first.path.substringBeforeLast('/').substringAfterLast('/')}")
            return null
        }

        val bestMatch = sorted[0].first
        Logger.d("FileRepository", "最佳匹配: ${bestMatch.name} (相似度: ${sorted[0].second})")
        return bestMatch
    }

    /**
     * 计算两个目录名的相似性
     * 使用编辑距离和公共子串计算相似度
     * @return 0.0 到 1.0 之间的相似度得分
     */
    private fun calculateDirectorySimilarity(dir1: String, dir2: String): Float {
        if (dir1.isEmpty() || dir2.isEmpty()) return 0.5f
        if (dir1.equals(dir2, ignoreCase = true)) return 1.0f

        // 检查是否包含相同的关键词
        val keywords1 = dir1.split(Regex("[\\s\\-_\\[\\]()（）]+")).filter { it.isNotEmpty() }
        val keywords2 = dir2.split(Regex("[\\s\\-_\\[\\]()（）]+")).filter { it.isNotEmpty() }

        val commonKeywords = keywords1.intersect(keywords2.toSet())
        if (commonKeywords.isNotEmpty()) {
            return 0.7f + (commonKeywords.size.toFloat() / maxOf(keywords1.size, keywords2.size) * 0.3f)
        }

        // 计算编辑距离相似度
        val maxLen = maxOf(dir1.length, dir2.length)
        if (maxLen == 0) return 1.0f

        val editDistance = levenshteinDistance(dir1.lowercase(), dir2.lowercase())
        return 1.0f - (editDistance.toFloat() / maxLen)
    }

    /**
     * 计算两个字符串的编辑距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
                }
            }
        }

        return dp[m][n]
    }

    /**
     * 删除本地原视频文件
     * @param videoInfo 要删除的视频信息
     * @return 是否删除成功
     */
    suspend fun deleteLocalVideo(videoInfo: LocalVideoInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(videoInfo.path)
            if (file.exists()) {
                file.delete()
            }

            // 同时从 MediaStore 中删除记录
            videoInfo.uri?.let { uri ->
                context.contentResolver.delete(uri, null, null)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 发送媒体库扫描广播
     * 强制刷新指定文件的媒体索引，防止缩略图损坏
     * @param filePath 文件路径
     */
    suspend fun triggerMediaScan(filePath: String) = withContext(Dispatchers.IO) {
        try {
            // 使用 MediaScannerConnection 扫描单个文件
            MediaScannerConnection.scanFile(
                context,
                arrayOf(filePath),
                null,
                null
            )

            // 同时发送传统广播（兼容旧版本）
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = Uri.fromFile(File(filePath))
            }
            context.sendBroadcast(mediaScanIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取本地存储空间信息
     * @return 可用空间大小（字节）
     */
    fun getAvailableSpace(): Long {
        val storageDir = Environment.getExternalStorageDirectory()
        return storageDir.freeSpace
    }
}
