package com.videosync.app.data

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import kotlin.coroutines.coroutineContext

/**
 * SMB 连接管理器
 * 封装 SMBJ 库的连接、文件列举、下载等操作
 * 所有网络操作均在 IO 调度器上执行
 */
class SmbManager {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    /**
     * 连接状态枚举
     */
    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    /**
     * 同步进度回调
     * @param fileName 当前处理的文件名
     * @param progress 当前进度 (0-100)
     * @param total 总文件数
     * @param current 当前第几个文件
     */
    data class SyncProgress(
        val fileName: String = "",
        val progress: Float = 0f,
        val total: Int = 0,
        val current: Int = 0
    )

    /**
     * 建立 SMB 连接
     * @param host NAS 服务器地址
     * @param port SMB 端口
     * @param username 用户名
     * @param password 密码
     * @param shareName 共享名称（默认 "video"）
     * @return 连接是否成功
     */
    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: String,
        shareName: String = "video"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 关闭已有连接
            disconnect()

            // 创建 SMB 客户端并连接
            client = SMBClient()
            connection = client!!.connect(host, port)

            // 建立认证会话
            val authContext = AuthenticationContext(
                username,
                password.toCharArray(),
                null
            )
            session = connection!!.authenticate(authContext)

            // 连接共享目录
            share = session!!.connectShare(shareName) as DiskShare

            true
        } catch (e: Exception) {
            e.printStackTrace()
            disconnect()
            false
        }
    }

    /**
     * 断开 SMB 连接并释放资源
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            share?.close()
            session?.close()
            connection?.close()
            client?.close()
        } catch (_: Exception) {
        } finally {
            share = null
            session = null
            connection = null
            client = null
        }
    }

    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean {
        return share != null && session != null && connection != null
    }

    /**
     * 列举远端目录下的所有文件
     * @param directoryPath 目录路径
     * @return 文件信息列表
     */
    suspend fun listFiles(directoryPath: String): List<RemoteFileInfo> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<RemoteFileInfo>()
            try {
                val currentShare = share ?: return@withContext result
                val entries = currentShare.list(directoryPath)

                for (entry in entries) {
                    coroutineContext.ensureActive()
                    // 跳过目录和隐藏文件
                    val fileName = entry.fileName
                    if (fileName == "." || fileName == "..") continue
                    if (entry.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L) continue

                    result.add(
                        RemoteFileInfo(
                            name = fileName,
                            size = entry.endOfFile,
                            path = if (directoryPath.endsWith("/")) {
                                "$directoryPath$fileName"
                            } else {
                                "$directoryPath/$fileName"
                            }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            result
        }

    /**
     * 从远端下载文件
     * @param remotePath 远端文件路径
     * @param outputStream 输出流（写入本地文件）
     * @param onProgress 下载进度回调 (已下载字节数, 总字节数)
     */
    suspend fun downloadFile(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentShare = share ?: return@withContext false

            // 以读取模式打开远端文件
            val remoteFile: File = currentShare.openFile(
                remotePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            val inputStream: InputStream = remoteFile.inputStream

            // 通过 DiskShare 获取文件大小
            val currentShare = share ?: throw IllegalStateException("共享未连接")
            val fileInfo = currentShare.getFileInformation(remotePath)
            val fileSize = fileInfo.standardInformation.endOfFile

            // 缓冲区读写
            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                coroutineContext.ensureActive()
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                onProgress(totalBytesRead, fileSize)
            }

            outputStream.flush()
            inputStream.close()
            remoteFile.close()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 检查远端文件是否存在
     * @param filePath 文件路径
     * @return 文件是否存在
     */
    suspend fun fileExists(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentShare = share ?: return@withContext false
            currentShare.fileExists(filePath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取远端文件大小
     * @param filePath 文件路径
     * @return 文件大小（字节），-1 表示获取失败
     */
    suspend fun getFileSize(filePath: String): Long = withContext(Dispatchers.IO) {
        try {
            val currentShare = share ?: return@withContext -1L
            val info = currentShare.getFileInformation(filePath)
            info.standardInformation.endOfFile
        } catch (e: Exception) {
            -1L
        }
    }
}

/**
 * 远端文件信息数据类
 */
data class RemoteFileInfo(
    val name: String,      // 文件名（含扩展名）
    val size: Long,        // 文件大小（字节）
    val path: String       // 完整路径
)
