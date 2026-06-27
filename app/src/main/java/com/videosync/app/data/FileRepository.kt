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
import com.videosync.app.util.VideoCodecHelper
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
            "m4v", "mpg", "mpeg", "3gp", "ts", "vob", "rmvb", "rm",
            "mts", "m2ts", "f4v", "asf", "divx", "ogv", "ogx", "mxf", "svi"
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
     * @param codec 视频编码格式（如 H.264, H.265）
     * @param isHighQualityCodec 是否为高质量编码（有对应 MKV 的前提）
     */
    data class LocalVideoInfo(
        val name: String,
        val extension: String,
        val path: String,
        val size: Long,
        val uri: Uri?,
        val codec: String = "",
        val isHighQualityCodec: Boolean = true
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
     * 同时扫描外部存储和内部存储，支持多种视频格式
     * @param useFileSystemScan 是否使用文件系统扫描作为补充（默认true）
     * @return 视频文件信息列表（按路径去重，保留所有不同路径的同名文件）
     */
    suspend fun scanAllVideos(useFileSystemScan: Boolean = true): List<LocalVideoInfo> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<LocalVideoInfo>()
        var scannedCount = 0
        var skippedCount = 0

        // 定义要扫描的存储卷列表：外部存储 + 内部存储
        val volumes = mutableListOf<Uri>()
        
        // 外部存储卷
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            volumes.add(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
        } else {
            volumes.add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
        
        // 内部存储卷（Android 10+ 支持）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                volumes.add(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_INTERNAL))
            } catch (e: Exception) {
                Logger.d("FileRepository", "内部存储卷不可用: ${e.message}")
            }
        }

        // 查询所有视频文件
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.VOLUME_NAME
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        // 遍历每个存储卷
        for (volumeUri in volumes) {
            try {
                context.contentResolver.query(
                    volumeUri,
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

                        // 构建 content URI（使用对应的存储卷URI）
                        val contentUri = ContentUris.withAppendedId(
                            volumeUri,
                            id
                        )

                        // 检测视频编码
                        val codecInfo = try {
                            VideoCodecHelper.detectCodec(context, path)
                        } catch (e: Exception) {
                            VideoCodecHelper.VideoCodecInfo()
                        }

                        videos.add(
                            LocalVideoInfo(
                                name = baseName,
                                extension = extension,
                                path = path,
                                size = size,
                                uri = contentUri,
                                codec = codecInfo.codec,
                                isHighQualityCodec = codecInfo.isHighQuality
                            )
                        )
                        scannedCount++
                    }
                }
            } catch (e: Exception) {
                Logger.d("FileRepository", "扫描存储卷失败: ${e.message}")
            }
        }

        Logger.i("FileRepository", "========== MediaStore扫描完成 ==========")
        Logger.i("FileRepository", "MediaStore发现视频: $scannedCount 个")
        Logger.i("FileRepository", "跳过已删除文件: $skippedCount 个")
        Logger.i("FileRepository", "扫描的存储卷: ${volumes.size} 个")

        // 使用文件系统扫描作为补充，查找MediaStore可能遗漏的视频
        if (useFileSystemScan) {
            Logger.i("FileRepository", "开始文件系统扫描作为补充...")
            val fsVideos = scanVideosFromFileSystem()
            Logger.i("FileRepository", "文件系统扫描发现: ${fsVideos.size} 个视频")
            
            // 合并结果，按路径去重
            val existingPaths = videos.map { it.path }.toSet()
            var newFromFs = 0
            for (fsVideo in fsVideos) {
                if (fsVideo.path !in existingPaths) {
                    videos.add(fsVideo)
                    newFromFs++
                    scannedCount++
                }
            }
            Logger.i("FileRepository", "文件系统新增视频: $newFromFs 个（排除重复）")
        } else {
            Logger.i("FileRepository", "跳过文件系统扫描（已禁用）")
        }

        Logger.i("FileRepository", "========== 扫描汇总 ==========")
        Logger.i("FileRepository", "最终视频总数: ${videos.size}")
        
        // 按完整路径去重（保留所有不同路径的同名文件）
        val result = videos.distinctBy { it.path }
        Logger.i("FileRepository", "去重后视频数: ${result.size}")
        result
    }

    /**
     * 使用文件系统直接扫描设备内所有视频文件
     * 作为MediaStore扫描的补充，查找可能被遗漏的视频
     * @return 视频文件信息列表
     */
    private suspend fun scanVideosFromFileSystem(): List<LocalVideoInfo> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<LocalVideoInfo>()
        
        Logger.i("FileRepository", "========== 文件系统扫描开始 ==========")
        Logger.i("FileRepository", "外部存储根目录: ${Environment.getExternalStorageDirectory().absolutePath}")
        
        // 定义要扫描的目录列表
        val scanDirectories = mutableListOf<File>()
        val skippedDirectories = mutableListOf<String>()
        
        // 外部存储根目录
        val externalStorage = Environment.getExternalStorageDirectory()
        if (externalStorage.exists() && externalStorage.canRead()) {
            scanDirectories.add(externalStorage)
            Logger.i("FileRepository", "✓ 添加外部存储根目录: ${externalStorage.absolutePath}")
        } else {
            Logger.w("FileRepository", "✗ 外部存储根目录不可访问: exists=${externalStorage.exists()}, canRead=${externalStorage.canRead()}")
        }
        
        // 常见的视频存储目录
        val commonVideoDirs = listOf(
            "Movies", "Video", "Videos", "DCIM", "Download", "Downloads",
            "Camera", "WhatsApp", "Telegram", "WeChat", "TikTok",
            "Bilibili", "Youku", "iQiyi", "Tencent Video",
            "QQ", "Douyin", "Kuaishou", "Xiaomi", "MIUI",
            "Pictures", "Recordings", "ScreenRecorder"
        )
        
        Logger.i("FileRepository", "检查常见视频目录...")
        for (dirName in commonVideoDirs) {
            val dir = File(externalStorage, dirName)
            if (dir.exists() && dir.canRead()) {
                if (!scanDirectories.contains(dir)) {
                    scanDirectories.add(dir)
                    Logger.i("FileRepository", "✓ 添加目录: ${dir.absolutePath}")
                }
            } else if (dir.exists()) {
                skippedDirectories.add("${dir.absolutePath} (无读取权限)")
                Logger.w("FileRepository", "✗ 跳过目录(无权限): ${dir.absolutePath}")
            }
        }
        
        // 检查 Android/media 目录（应用媒体文件）
        val androidMediaDir = File(externalStorage, "Android/media")
        if (androidMediaDir.exists() && androidMediaDir.canRead()) {
            scanDirectories.add(androidMediaDir)
            Logger.i("FileRepository", "✓ 添加 Android/media 目录: ${androidMediaDir.absolutePath}")
        }
        
        Logger.i("FileRepository", "待扫描目录总数: ${scanDirectories.size}")
        Logger.i("FileRepository", "跳过目录数: ${skippedDirectories.size}")
        
        // 递归扫描每个目录
        var totalFilesScanned = 0
        for (scanDir in scanDirectories) {
            try {
                Logger.i("FileRepository", "--- 开始扫描目录: ${scanDir.absolutePath} ---")
                val videosBefore = videos.size
                scanDirectoryRecursive(scanDir, videos, 0)
                val newVideos = videos.size - videosBefore
                Logger.i("FileRepository", "--- 目录扫描完成: ${scanDir.absolutePath}, 发现 $newVideos 个视频 ---")
                totalFilesScanned++
            } catch (e: Exception) {
                Logger.e("FileRepository", "扫描目录失败 ${scanDir.absolutePath}: ${e.message}", e)
            }
        }
        
        Logger.i("FileRepository", "========== 文件系统扫描完成 ==========")
        Logger.i("FileRepository", "已扫描目录数: $totalFilesScanned")
        Logger.i("FileRepository", "发现视频总数: ${videos.size}")
        
        videos
    }

    /**
     * 递归扫描目录中的视频文件
     * @param directory 要扫描的目录
     * @param videos 视频列表（用于收集结果）
     * @param depth 当前递归深度（用于日志缩进）
     */
    private suspend fun scanDirectoryRecursive(directory: File, videos: MutableList<LocalVideoInfo>, depth: Int) {
        coroutineContext.ensureActive()
        
        if (!directory.exists()) {
            Logger.d("FileRepository", "${"  ".repeat(depth)}目录不存在: ${directory.absolutePath}")
            return
        }
        
        if (!directory.canRead()) {
            Logger.w("FileRepository", "${"  ".repeat(depth)}无读取权限: ${directory.absolutePath}")
            return
        }
        
        val files = directory.listFiles()
        if (files == null) {
            Logger.w("FileRepository", "${"  ".repeat(depth)}无法列出文件: ${directory.absolutePath}")
            return
        }
        
        // 只在前几层深度记录目录遍历，避免日志过多
        if (depth <= 2) {
            Logger.d("FileRepository", "${"  ".repeat(depth)}扫描目录: ${directory.name}/ (${files.size} 项)")
        }
        
        for (file in files) {
            coroutineContext.ensureActive()
            
            if (file.isDirectory) {
                // 跳过隐藏目录和系统目录
                if (file.name.startsWith(".")) {
                    continue
                }
                if (file.name == "Android" && depth == 0) {
                    // 只扫描 Android/media 子目录
                    val androidMedia = File(file, "media")
                    if (androidMedia.exists() && androidMedia.canRead()) {
                        scanDirectoryRecursive(androidMedia, videos, depth + 1)
                    }
                    continue
                }
                scanDirectoryRecursive(file, videos, depth + 1)
            } else if (file.isFile) {
                val extension = file.extension.lowercase()
                if (extension in VIDEO_EXTENSIONS) {
                    val baseName = file.nameWithoutExtension
                    val path = file.absolutePath
                    val size = file.length()
                    
                    // 检测视频编码
                    val codecInfo = try {
                        VideoCodecHelper.detectCodec(context, path)
                    } catch (e: Exception) {
                        VideoCodecHelper.VideoCodecInfo()
                    }
                    
                    videos.add(
                        LocalVideoInfo(
                            name = baseName,
                            extension = extension,
                            path = path,
                            size = size,
                            uri = null, // 文件系统扫描无法获取MediaStore URI
                            codec = codecInfo.codec,
                            isHighQualityCodec = codecInfo.isHighQuality
                        )
                    )
                    
                    if (depth <= 2) {
                        Logger.d("FileRepository", "${"  ".repeat(depth)}  发现视频: ${file.name} (${formatFileSize(size)}, ${codecInfo.codec})")
                    }
                }
            }
        }
    }
    
    /**
     * 格式化文件大小为可读字符串
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024.0))}MB"
            else -> "${"%.2f".format(size / (1024.0 * 1024.0 * 1024.0))}GB"
        }
    }

    /**
     * 检查远端是否有匹配的文件
     * 优先匹配目录结构相似的文件，避免错误匹配
     *
     * 匹配逻辑：
     * - HEVC/VP9/AV1 编码的视频 → 匹配原格式文件（NAS 上保持原样）
     * - H.264/MPEG-4 等其他编码 → 匹配 MKV 文件（NAS 转码后的）
     * - 如果文件后缀和大小完全一致 → 跳过（已是同一文件）
     *
     * @param localPath 本地视频完整路径
     * @param localName 本地视频基础名（不含扩展名）
     * @param localExtension 本地视频扩展名
     * @param localSize 本地视频文件大小
     * @param localCodec 本地视频编码格式
     * @param remoteFiles 远端文件列表
     * @return 匹配的远端文件信息，null 表示无匹配或无需替换
     */
    fun findMatchingRemoteFile(
        localPath: String,
        localName: String,
        localExtension: String,
        localSize: Long,
        localCodec: String,
        remoteFiles: List<RemoteFileInfo>
    ): RemoteFileInfo? {
        // 获取本地文件的上一级目录名
        val localParentDir = localPath.substringBeforeLast('/').substringAfterLast('/')

        // 判断本地视频编码是否为 NAS 不需要转码的格式
        val keepOriginalFormat = localCodec in setOf("H.265", "HEVC", "VP9", "AV1")

        // 找到所有同名且格式匹配的远端文件
        val candidates = remoteFiles.filter { remote ->
            val remoteBaseName = remote.name.substringBeforeLast('.')
            val remoteExtension = remote.name.substringAfterLast('.').lowercase()

            if (keepOriginalFormat) {
                // HEVC/VP9/AV1 编码 → 匹配原格式文件
                remoteBaseName == localName && remoteExtension == localExtension.lowercase()
            } else {
                // H.264/MPEG-4 等 → 匹配 HQ 格式文件（mkv, mp4 等）
                remoteBaseName == localName &&
                        remoteExtension in HQ_EXTENSIONS &&
                        remoteExtension != localExtension.lowercase()
            }
        }

        if (candidates.isEmpty()) return null

        // 过滤掉后缀和大小完全一致的文件（说明已是同一文件，无需替换）
        val filteredCandidates = candidates.filter { remote ->
            val remoteExtension = remote.name.substringAfterLast('.').lowercase()
            val isSameFile = remoteExtension == localExtension.lowercase() && remote.size == localSize
            if (isSameFile) {
                Logger.d("FileRepository", "跳过相同文件: $localName (后缀相同，大小一致: ${localSize}B)")
            }
            !isSameFile
        }

        if (filteredCandidates.isEmpty()) return null

        // 计算每个候选文件的目录相似性得分
        val scoredCandidates = filteredCandidates.map { remote ->
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
