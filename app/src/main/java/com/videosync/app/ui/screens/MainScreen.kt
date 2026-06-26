package com.videosync.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videosync.app.data.FileRepository
import com.videosync.app.data.NasConfig
import com.videosync.app.data.SettingsDataStore
import com.videosync.app.data.SmbManager
import com.videosync.app.ui.theme.StatusConnected
import com.videosync.app.ui.theme.StatusDisconnected
import com.videosync.app.ui.theme.StatusSyncing
import com.videosync.app.util.BatteryOptimizationHelper
import com.videosync.app.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 传输任务状态枚举
 */
enum class TaskStatus {
    PENDING,    // 等待传输
    DOWNLOADING,// 下载中
    VERIFYING,  // 校验中
    COMPLETED,  // 已完成
    FAILED      // 失败
}

/**
 * 同步预览项数据类
 * @param fileName 文件名
 * @param localPath 本地文件路径
 * @param remotePath 远端文件路径
 * @param localSize 本地文件大小
 * @param remoteSize 远端文件大小
 */
data class SyncPreviewItem(
    val fileName: String,
    val localPath: String,
    val remotePath: String,
    val localSize: Long,
    val remoteSize: Long
)

/**
 * 传输任务数据类
 * @param fileName 文件名
 * @param localSize 本地原文件大小
 * @param remoteSize 远端文件大小
 * @param downloadedSize 已下载大小
 * @param status 任务状态
 * @param progress 下载进度 0-100
 * @param errorMessage 错误信息
 */
data class TransferTask(
    val fileName: String,
    val localSize: Long,
    val remoteSize: Long,
    val downloadedSize: Long = 0,
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Float = 0f,
    val errorMessage: String = ""
)

/**
 * 主界面 Composable
 * 包含配置表单、状态看板、任务列表和进度显示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 数据层实例
    val settingsDataStore = remember { SettingsDataStore(context) }
    val fileRepository = remember { FileRepository(context) }
    val smbManager = remember { SmbManager() }

    // 从 DataStore 读取历史配置
    val savedConfig by settingsDataStore.nasConfigFlow.collectAsState(initial = NasConfig())

    // 表单状态
    var nasHost by remember { mutableStateOf("") }
    var nasPort by remember { mutableStateOf("445") }
    var shareName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remotePath by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // 远端目录浏览器状态
    var showDirBrowser by remember { mutableStateOf(false) }
    val remoteDirectories = remember { mutableStateListOf<String>() }
    var isLoadingDirectories by remember { mutableStateOf(false) }

    // 同步预览对话框状态
    var showSyncPreview by remember { mutableStateOf(false) }
    val syncPreviewItems = remember { mutableStateListOf<SyncPreviewItem>() }
    val selectedItems = remember { mutableStateListOf<Int>() }

    // 连接与同步状态
    var isConnected by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var hasStoragePermission by remember { mutableStateOf(false) }
    var isIgnoringBatteryOptimization by remember { mutableStateOf(false) }

    // 电池优化请求启动器
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 重新检查电池优化状态
        isIgnoringBatteryOptimization = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        if (isIgnoringBatteryOptimization) {
            scope.launch {
                snackbarHostState.showSnackbar("电池优化已关闭，可以后台运行")
            }
        }
    }

    // 传输任务列表
    val transferTasks = remember { mutableStateListOf<TransferTask>() }

    // 当前处理索引
    var currentTaskIndex by remember { mutableIntStateOf(-1) }

    // 权限请求启动器
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission = fileRepository.hasStoragePermission()
        if (hasStoragePermission) {
            scope.launch {
                snackbarHostState.showSnackbar("存储权限已授予")
            }
        }
    }

    // 传统存储权限请求（Android 10 以下）
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasStoragePermission = permissions.values.all { it }
        if (hasStoragePermission) {
            scope.launch {
                snackbarHostState.showSnackbar("存储权限已授予")
            }
        }
    }

    // 启动时自动填充历史配置
    LaunchedEffect(savedConfig) {
        if (savedConfig.host.isNotEmpty()) {
            nasHost = savedConfig.host
            nasPort = savedConfig.port.toString()
            shareName = savedConfig.shareName
            username = savedConfig.username
            password = savedConfig.password
            remotePath = savedConfig.remotePath
        }
    }

    // 启动时检查权限状态
    LaunchedEffect(Unit) {
        hasStoragePermission = fileRepository.hasStoragePermission()
        isIgnoringBatteryOptimization = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "视频同步助手",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // 日志导出按钮
                    IconButton(
                        onClick = {
                            scope.launch {
                                val logFile = Logger.exportLogs(context)
                                if (logFile != null) {
                                    val shareIntent = Logger.createShareIntent(context, logFile)
                                    context.startActivity(Intent.createChooser(shareIntent, "导出日志"))
                                    Logger.i("MainScreen", "日志已导出: ${logFile.absolutePath}")
                                } else {
                                    snackbarHostState.showSnackbar("日志导出失败")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "导出日志"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            // 主操作按钮 - 开始/停止同步
            ExtendedFloatingActionButton(
                onClick = {
                    if (isSyncing) {
                        // 停止同步
                        isSyncing = false
                        scope.launch {
                            smbManager.disconnect()
                            snackbarHostState.showSnackbar("同步已停止")
                        }
                    } else {
                        // 扫描并显示预览对话框
                        scope.launch {
                            if (nasHost.isEmpty() || shareName.isEmpty() || username.isEmpty()) {
                                snackbarHostState.showSnackbar("请先填写服务器地址、共享文件夹和用户名")
                                return@launch
                            }
                            if (!fileRepository.hasStoragePermission()) {
                                snackbarHostState.showSnackbar("请先授予存储权限")
                                return@launch
                            }

                            snackbarHostState.showSnackbar("正在扫描匹配文件...")

                            try {
                                // 扫描本地视频
                                val localVideos = fileRepository.scanAllVideos()
                                Logger.i("Preview", "本地视频扫描完成，共 ${localVideos.size} 个文件")

                                // 连接 SMB 获取远端文件
                                val connected = smbManager.connect(
                                    host = nasHost,
                                    port = nasPort.toIntOrNull() ?: 445,
                                    username = username,
                                    password = password,
                                    shareName = shareName
                                )

                                if (!connected) {
                                    snackbarHostState.showSnackbar("连接 NAS 失败，请检查配置")
                                    return@launch
                                }

                                isConnected = true

                                // 获取远端文件列表（递归包含子目录）
                                snackbarHostState.showSnackbar("正在获取远端文件列表...")
                                val remoteFiles = smbManager.listFilesRecursively(remotePath.ifEmpty { "/" })
                                Logger.i("Preview", "远端文件列表获取完成，共 ${remoteFiles.size} 个文件")

                                // 查找匹配项
                                val previewItems = mutableListOf<SyncPreviewItem>()
                                for (local in localVideos) {
                                    val match = fileRepository.findMatchingRemoteFile(
                                        localName = local.name,
                                        localExtension = local.extension,
                                        remoteFiles = remoteFiles
                                    )
                                    if (match != null) {
                                        previewItems.add(
                                            SyncPreviewItem(
                                                fileName = local.name,
                                                localPath = local.path,
                                                remotePath = match.path,
                                                localSize = local.size,
                                                remoteSize = match.size
                                            )
                                        )
                                    }
                                }

                                smbManager.disconnect()
                                isConnected = false

                                if (previewItems.isEmpty()) {
                                    snackbarHostState.showSnackbar("未找到可匹配的高画质文件")
                                    return@launch
                                }

                                // 显示预览对话框
                                syncPreviewItems.clear()
                                syncPreviewItems.addAll(previewItems)
                                selectedItems.clear()
                                selectedItems.addAll(previewItems.indices)
                                showSyncPreview = true

                                Logger.i("Preview", "找到 ${previewItems.size} 个匹配文件，等待用户确认")

                            } catch (e: Exception) {
                                Logger.e("Preview", "扫描匹配文件失败", e)
                                snackbarHostState.showSnackbar("扫描失败：${e.message}")
                                smbManager.disconnect()
                                isConnected = false
                            }
                        }
                    }
                },
                containerColor = if (isSyncing) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (isSyncing) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            ) {
                Icon(
                    imageVector = if (isSyncing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isSyncing) "停止同步" else "开始同步"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isSyncing) "停止同步" else "开始同步")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 权限状态卡片
            item {
                Spacer(modifier = Modifier.height(8.dp))
                PermissionCard(
                    hasPermission = hasStoragePermission,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            storagePermissionLauncher.launch(intent)
                        } else {
                            legacyPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            )
                        }
                    }
                )
            }

            // 电池优化状态卡片
            item {
                BatteryOptimizationCard(
                    isIgnoring = isIgnoringBatteryOptimization,
                    onRequestIgnore = {
                        if (BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                            isIgnoringBatteryOptimization = true
                            scope.launch {
                                snackbarHostState.showSnackbar("电池优化已关闭")
                            }
                        } else {
                            // 先尝试直接请求
                            val intent = BatteryOptimizationHelper.getRequestIgnoreBatteryOptimizationsIntent(context)
                            batteryOptimizationLauncher.launch(intent)
                        }
                    }
                )
            }

            // NAS 配置表单
            item {
                NasConfigForm(
                    nasHost = nasHost,
                    onNasHostChange = { nasHost = it },
                    nasPort = nasPort,
                    onNasPortChange = { nasPort = it },
                    shareName = shareName,
                    onShareNameChange = { shareName = it },
                    username = username,
                    onUsernameChange = { username = it },
                    password = password,
                    onPasswordChange = { password = it },
                    passwordVisible = passwordVisible,
                    onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                    remotePath = remotePath,
                    onRemotePathChange = { remotePath = it },
                    onBrowseRemotePath = {
                        // 打开目录浏览器
                        scope.launch {
                            if (nasHost.isEmpty() || shareName.isEmpty() || username.isEmpty()) {
                                snackbarHostState.showSnackbar("请先填写服务器地址、共享文件夹和用户名")
                                return@launch
                            }
                            isLoadingDirectories = true
                            try {
                                val connected = smbManager.connect(
                                    host = nasHost,
                                    port = nasPort.toIntOrNull() ?: 445,
                                    username = username,
                                    password = password,
                                    shareName = shareName
                                )
                                if (connected) {
                                    val dirs = smbManager.listDirectories(remotePath.ifEmpty { "/" })
                                    remoteDirectories.clear()
                                    remoteDirectories.addAll(dirs)
                                    showDirBrowser = true
                                    smbManager.disconnect()
                                } else {
                                    snackbarHostState.showSnackbar("连接失败，请检查配置")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("获取目录失败：${e.message}")
                            } finally {
                                isLoadingDirectories = false
                            }
                        }
                    }
                )
            }

            // 连接状态卡片
            item {
                ConnectionStatusCard(
                    isConnected = isConnected,
                    isSyncing = isSyncing,
                    nasHost = nasHost,
                    taskCount = transferTasks.size,
                    completedCount = transferTasks.count { it.status == TaskStatus.COMPLETED }
                )
            }

            // 整体进度概览（仅在同步时显示）
            if (isSyncing && transferTasks.isNotEmpty()) {
                item {
                    OverallProgressCard(
                        tasks = transferTasks,
                        currentIndex = currentTaskIndex
                    )
                }
            }

            // 待传输任务列表标题
            if (transferTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "传输任务列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 传输任务列表
            itemsIndexed(transferTasks) { index, task ->
                TransferTaskItem(
                    task = task,
                    index = index,
                    isActive = index == currentTaskIndex
                )
            }

            // 底部间距（避免被 FAB 遮挡）
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // 远端目录浏览器对话框
    if (showDirBrowser) {
        RemoteDirBrowserDialog(
            currentPath = remotePath.ifEmpty { "/" },
            directories = remoteDirectories,
            isLoading = isLoadingDirectories,
            onSelect = { selectedPath ->
                remotePath = selectedPath
                showDirBrowser = false
            },
            onRefresh = {
                scope.launch {
                    isLoadingDirectories = true
                    try {
                        val connected = smbManager.connect(
                            host = nasHost,
                            port = nasPort.toIntOrNull() ?: 445,
                            username = username,
                            password = password,
                            shareName = shareName
                        )
                        if (connected) {
                            val dirs = smbManager.listDirectories(remotePath.ifEmpty { "/" })
                            remoteDirectories.clear()
                            remoteDirectories.addAll(dirs)
                            smbManager.disconnect()
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("刷新失败：${e.message}")
                    } finally {
                        isLoadingDirectories = false
                    }
                }
            },
            onNavigateUp = { parentPath ->
                scope.launch {
                    isLoadingDirectories = true
                    try {
                        val connected = smbManager.connect(
                            host = nasHost,
                            port = nasPort.toIntOrNull() ?: 445,
                            username = username,
                            password = password,
                            shareName = shareName
                        )
                        if (connected) {
                            val dirs = smbManager.listDirectories(parentPath)
                            remoteDirectories.clear()
                            remoteDirectories.addAll(dirs)
                            remotePath = parentPath
                            smbManager.disconnect()
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("导航失败：${e.message}")
                    } finally {
                        isLoadingDirectories = false
                    }
                }
            },
            onDismiss = { showDirBrowser = false }
        )
    }

    // 同步预览对话框
    if (showSyncPreview) {
        SyncPreviewDialog(
            items = syncPreviewItems,
            selectedIndices = selectedItems,
            onToggleSelection = { index ->
                if (index in selectedItems) {
                    selectedItems.remove(index)
                } else {
                    selectedItems.add(index)
                }
            },
            onSelectAll = {
                if (selectedItems.size == syncPreviewItems.size) {
                    selectedItems.clear()
                } else {
                    selectedItems.clear()
                    selectedItems.addAll(syncPreviewItems.indices)
                }
            },
            onConfirm = {
                showSyncPreview = false
                // 开始同步选中的任务
                transferTasks.clear()
                currentTaskIndex = -1
                val selectedPreviewItems = syncPreviewItems.filterIndexed { index, _ -> index in selectedItems }
                scope.launch {
                    startSyncWithPreview(
                        nasHost = nasHost,
                        nasPort = nasPort,
                        shareName = shareName,
                        username = username,
                        password = password,
                        smbManager = smbManager,
                        fileRepository = fileRepository,
                        settingsDataStore = settingsDataStore,
                        snackbarHostState = snackbarHostState,
                        previewItems = selectedPreviewItems,
                        onConnected = { isConnected = true },
                        onDisconnected = { isConnected = false },
                        onTaskListReady = { tasks ->
                            transferTasks.clear()
                            transferTasks.addAll(tasks)
                        },
                        onTaskStart = { index ->
                            currentTaskIndex = index
                        },
                        onTaskProgress = { index, progress, downloadedSize ->
                            if (index in transferTasks.indices) {
                                transferTasks[index] = transferTasks[index].copy(
                                    progress = progress,
                                    downloadedSize = downloadedSize,
                                    status = TaskStatus.DOWNLOADING
                                )
                            }
                        },
                        onTaskVerifying = { index ->
                            if (index in transferTasks.indices) {
                                transferTasks[index] = transferTasks[index].copy(
                                    status = TaskStatus.VERIFYING
                                )
                            }
                        },
                        onTaskComplete = { index ->
                            if (index in transferTasks.indices) {
                                transferTasks[index] = transferTasks[index].copy(
                                    progress = 100f,
                                    downloadedSize = transferTasks[index].remoteSize,
                                    status = TaskStatus.COMPLETED
                                )
                            }
                        },
                        onTaskFailed = { index, error ->
                            if (index in transferTasks.indices) {
                                transferTasks[index] = transferTasks[index].copy(
                                    status = TaskStatus.FAILED,
                                    errorMessage = error
                                )
                            }
                        },
                        onSyncComplete = {
                            isSyncing = false
                            currentTaskIndex = -1
                        },
                        onError = { error ->
                            isSyncing = false
                            isConnected = false
                            scope.launch {
                                snackbarHostState.showSnackbar(error)
                            }
                        }
                    )
                }
            },
            onDismiss = { showSyncPreview = false }
        )
    }
}

/**
 * 权限状态卡片
 */
@Composable
private fun PermissionCard(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (hasPermission) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (hasPermission) Icons.Default.Security else Icons.Default.Lock,
                contentDescription = null,
                tint = if (hasPermission) StatusConnected else StatusDisconnected,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasPermission) "存储权限已授予" else "需要存储权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (hasPermission) {
                        "应用可以访问设备上的所有视频文件"
                    } else {
                        "请授予所有文件访问权限以扫描视频"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!hasPermission) {
                Button(onClick = onRequestPermission) {
                    Text("授予权限")
                }
            }
        }
    }
}

/**
 * 电池优化状态卡片
 * 引导用户关闭电池优化，确保后台同步不被系统杀死
 */
@Composable
private fun BatteryOptimizationCard(
    isIgnoring: Boolean,
    onRequestIgnore: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isIgnoring) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isIgnoring) Icons.Default.CheckCircle else Icons.Default.Pending,
                contentDescription = null,
                tint = if (isIgnoring) StatusConnected else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isIgnoring) "电池优化已关闭" else "建议关闭电池优化",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isIgnoring) {
                        "应用可以在后台持续运行同步任务"
                    } else {
                        "关闭后可确保同步任务不被系统中断"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isIgnoring) {
                Button(onClick = onRequestIgnore) {
                    Text("关闭优化")
                }
            }
        }
    }
}

/**
 * NAS 配置表单
 * 包含服务器地址、共享文件夹、用户名、密码、远端路径
 */
@Composable
private fun NasConfigForm(
    nasHost: String,
    onNasHostChange: (String) -> Unit,
    nasPort: String,
    onNasPortChange: (String) -> Unit,
    shareName: String,
    onShareNameChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    remotePath: String,
    onRemotePathChange: (String) -> Unit,
    onBrowseRemotePath: () -> Unit = {}
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "NAS 连接配置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // NAS 服务器地址输入框
            OutlinedTextField(
                value = nasHost,
                onValueChange = onNasHostChange,
                label = { Text("服务器地址") },
                placeholder = { Text("例如：192.168.1.100") },
                leadingIcon = {
                    Icon(Icons.Default.Cloud, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // SMB 端口输入框
            OutlinedTextField(
                value = nasPort,
                onValueChange = onNasPortChange,
                label = { Text("SMB 端口") },
                placeholder = { Text("默认：445") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // 共享文件夹名称输入框
            OutlinedTextField(
                value = shareName,
                onValueChange = onShareNameChange,
                label = { Text("共享文件夹") },
                placeholder = { Text("例如：video、media") },
                leadingIcon = {
                    Icon(Icons.Default.Folder, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 用户名输入框
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("用户名") },
                placeholder = { Text("NAS 登录用户名") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 密码输入框（带显示/隐藏切换）
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("密码") },
                placeholder = { Text("NAS 登录密码") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = onPasswordVisibilityToggle) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            // 远端子目录路径输入框（带浏览按钮）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = remotePath,
                    onValueChange = onRemotePathChange,
                    label = { Text("远端子目录路径") },
                    placeholder = { Text("共享文件夹下的相对路径，如 /movies") },
                    leadingIcon = {
                        Icon(Icons.Default.Folder, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 浏览目录按钮
                IconButton(
                    onClick = onBrowseRemotePath,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "浏览目录",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * 同步预览对话框
 * 显示匹配的视频列表，支持选择性处理
 */
@Composable
private fun SyncPreviewDialog(
    items: List<SyncPreviewItem>,
    selectedIndices: List<Int>,
    onToggleSelection: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "同步预览",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "已选 ${selectedIndices.size}/${items.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "请选择要同步的视频文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                // 全选/取消全选按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectAll() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = selectedIndices.size == items.size,
                        onCheckedChange = { onSelectAll() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedIndices.size == items.size) "取消全选" else "全选",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(modifier = Modifier.height(4.dp))

                // 文件列表
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(items) { index, item ->
                        val isSelected = index in selectedIndices
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .clickable { onToggleSelection(index) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelection(index) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                // 文件名
                                Text(
                                    text = item.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // 本地路径
                                Text(
                                    text = "本地：${item.localPath.substringAfterLast('/')}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // 远端路径
                                Text(
                                    text = "远端：${item.remotePath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // 文件大小对比
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = formatFileSize(item.remoteSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "替换 ${formatFileSize(item.localSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedIndices.isNotEmpty()
            ) {
                Text("开始同步 (${selectedIndices.size}个)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 执行同步流程的核心函数
 */
private suspend fun startSync(
    nasHost: String,
    nasPort: String,
    shareName: String,
    username: String,
    password: String,
    remotePath: String,
    smbManager: SmbManager,
    fileRepository: FileRepository,
    settingsDataStore: SettingsDataStore,
    snackbarHostState: SnackbarHostState,
    onConnecting: () -> Unit,
    onConnected: () -> Unit,
    onDisconnected: () -> Unit,
    onTaskListReady: (List<TransferTask>) -> Unit,
    onTaskStart: (Int) -> Unit,
    onTaskProgress: (Int, Float, Long) -> Unit,
    onTaskVerifying: (Int) -> Unit,
    onTaskComplete: (Int) -> Unit,
    onTaskFailed: (Int, String) -> Unit,
    onSyncComplete: () -> Unit,
    onError: (String) -> Unit
) {
    Logger.i("Sync", "开始同步流程 - 服务器: $nasHost, 共享: $shareName, 用户: $username")

    // 参数验证
    if (nasHost.isEmpty()) {
        Logger.w("Sync", "参数验证失败: 服务器地址为空")
        onError("请输入服务器地址")
        return
    }
    if (shareName.isEmpty()) {
        Logger.w("Sync", "参数验证失败: 共享文件夹为空")
        onError("请输入共享文件夹名称")
        return
    }
    if (username.isEmpty()) {
        Logger.w("Sync", "参数验证失败: 用户名为空")
        onError("请输入用户名")
        return
    }

    // 检查存储权限
    if (!fileRepository.hasStoragePermission()) {
        Logger.w("Sync", "存储权限未授予")
        onError("请先授予存储权限")
        return
    }

    val port = nasPort.toIntOrNull() ?: 445
    Logger.d("Sync", "解析端口: $port")

    try {
        // 保存配置到 DataStore
        settingsDataStore.saveNasConfig(
            NasConfig(
                host = nasHost,
                port = port,
                shareName = shareName,
                username = username,
                password = password,
                remotePath = remotePath
            )
        )

        // 建立 SMB 连接
        onConnecting()
        val connected = smbManager.connect(
            host = nasHost,
            port = port,
            username = username,
            password = password,
            shareName = shareName
        )

        if (!connected) {
            Logger.e("Sync", "SMB 连接失败 - 服务器: $nasHost:$port")
            onError("无法连接到 NAS，请检查网络和凭据")
            onDisconnected()
            return
        }

        Logger.i("Sync", "SMB 连接成功 - 服务器: $nasHost:$port")
        onConnected()

        // 扫描本地视频
        snackbarHostState.showSnackbar("正在扫描本地视频文件...")
        val localVideos = fileRepository.scanAllVideos()
        Logger.i("Sync", "本地视频扫描完成，共 ${localVideos.size} 个文件")

        if (localVideos.isEmpty()) {
            Logger.w("Sync", "未找到本地视频文件")
            onError("未找到本地视频文件")
            smbManager.disconnect()
            onDisconnected()
            onSyncComplete()
            return
        }

        // 获取远端文件列表（递归包含子目录）
        snackbarHostState.showSnackbar("正在获取远端文件列表（包含子目录）...")
        val remoteFiles = smbManager.listFilesRecursively(remotePath.ifEmpty { "/" })
        Logger.i("Sync", "远端文件列表获取完成，共 ${remoteFiles.size} 个文件")

        // 查找匹配项并构建任务列表
        val matchQueue = localVideos.mapNotNull { local ->
            val match = fileRepository.findMatchingRemoteFile(
                localName = local.name,
                localExtension = local.extension,
                remoteFiles = remoteFiles
            )
            if (match != null) {
                Logger.d("Sync", "找到匹配文件: ${local.name} -> ${match.name}")
                Pair(local, match)
            } else {
                null
            }
        }

        if (matchQueue.isEmpty()) {
            Logger.w("Sync", "未找到可匹配的高画质文件")
            onError("未找到可匹配的高画质文件")
            smbManager.disconnect()
            onDisconnected()
            onSyncComplete()
            return
        }

        Logger.i("Sync", "找到 ${matchQueue.size} 个匹配文件，开始同步")

        // 构建任务列表
        val tasks = matchQueue.map { (local, remote) ->
            TransferTask(
                fileName = "${local.name}.${remote.name.substringAfterLast('.')}",
                localSize = local.size,
                remoteSize = remote.size,
                status = TaskStatus.PENDING
            )
        }
        onTaskListReady(tasks)

        // 逐个处理任务
        for ((index, pair) in matchQueue.withIndex()) {
            val (localVideo, remoteFile) = pair
            val targetFileName = "${localVideo.name}.${remoteFile.name.substringAfterLast('.')}"
            val targetPath = "${localVideo.path.substringBeforeLast('/')}/$targetFileName"

            onTaskStart(index)

            try {
                Logger.d("Sync", "开始处理任务 [$index]: ${localVideo.name}")

                // 检查磁盘空间
                val availableSpace = fileRepository.getAvailableSpace()
                if (availableSpace < remoteFile.size) {
                    Logger.w("Sync", "磁盘空间不足: 需要 ${remoteFile.size}B, 可用 ${availableSpace}B")
                    onTaskFailed(index, "磁盘空间不足")
                    continue
                }

                // 下载文件到临时路径
                val tempPath = "$targetPath.tmp"
                val tempFile = java.io.File(tempPath)
                val outputStream = tempFile.outputStream()

                Logger.i("Sync", "开始下载: ${remoteFile.path} -> $tempPath")

                val downloadSuccess = smbManager.downloadFile(
                    remotePath = remoteFile.path,
                    outputStream = outputStream,
                    onProgress = { downloaded, total ->
                        val progress = if (total > 0) {
                            (downloaded.toFloat() / total * 100)
                        } else 0f
                        onTaskProgress(index, progress, downloaded)
                    }
                )
                outputStream.close()

                if (!downloadSuccess) {
                    Logger.e("Sync", "下载失败: ${localVideo.name}")
                    tempFile.delete()
                    onTaskFailed(index, "下载失败")
                    continue
                }

                Logger.i("Sync", "下载完成: ${localVideo.name}")

                // 防丢校验 - 严格比对文件大小
                onTaskVerifying(index)

                val localDownloadedSize = tempFile.length()
                val remoteFileSize = remoteFile.size

                Logger.d("Sync", "开始完整性校验: 远端=${remoteFileSize}B, 本地=${localDownloadedSize}B")

                if (localDownloadedSize != remoteFileSize) {
                    // 大小不一致，严禁删除原视频
                    Logger.e("Sync", "完整性校验失败! 文件大小不一致: ${localVideo.name}")
                    tempFile.delete()
                    onTaskFailed(index, "校验失败：远端${remoteFileSize}B，本地${localDownloadedSize}B")
                    continue
                }

                Logger.i("Sync", "完整性校验通过: ${localVideo.name}")

                // 校验通过，重命名临时文件为目标文件
                val targetFile = java.io.File(targetPath)
                if (targetFile.exists()) targetFile.delete()
                val renameSuccess = tempFile.renameTo(targetFile)
                if (!renameSuccess) {
                    Logger.e("Sync", "文件重命名失败: $targetPath")
                    tempFile.delete()
                    onTaskFailed(index, "文件重命名失败")
                    continue
                }

                // 删除原视频
                val deleteSuccess = fileRepository.deleteLocalVideo(localVideo)
                if (!deleteSuccess) {
                    Logger.w("Sync", "原视频删除失败: ${localVideo.path}")
                    snackbarHostState.showSnackbar("警告：原视频删除失败，请手动处理")
                } else {
                    Logger.i("Sync", "原视频已删除: ${localVideo.path}")
                }

                // 触发媒体库扫描
                fileRepository.triggerMediaScan(targetPath)
                Logger.i("Sync", "任务完成: ${localVideo.name}")

                onTaskComplete(index)

            } catch (e: Exception) {
                Logger.e("Sync", "处理任务异常: ${localVideo.name}", e)
                e.printStackTrace()
                val tempFile = java.io.File("$targetPath.tmp")
                if (tempFile.exists()) tempFile.delete()
                onTaskFailed(index, e.message ?: "未知错误")
            }
        }

        // 同步完成
        val completedCount = tasks.count { it.status == TaskStatus.COMPLETED }
        Logger.i("Sync", "同步流程完成: 成功 $completedCount/${tasks.size}")
        snackbarHostState.showSnackbar("同步完成，成功处理 $completedCount/${tasks.size} 个文件")

    } catch (e: Exception) {
        Logger.e("Sync", "同步过程异常", e)
        onError("同步过程出错：${e.message}")
    } finally {
        smbManager.disconnect()
        onDisconnected()
        onSyncComplete()
        Logger.i("Sync", "同步流程结束，连接已断开")
    }
}

/**
 * 使用预览结果执行同步流程
 * @param previewItems 用户在预览对话框中选择的文件列表
 */
private suspend fun startSyncWithPreview(
    nasHost: String,
    nasPort: String,
    shareName: String,
    username: String,
    password: String,
    smbManager: SmbManager,
    fileRepository: FileRepository,
    settingsDataStore: SettingsDataStore,
    snackbarHostState: SnackbarHostState,
    previewItems: List<SyncPreviewItem>,
    onConnected: () -> Unit,
    onDisconnected: () -> Unit,
    onTaskListReady: (List<TransferTask>) -> Unit,
    onTaskStart: (Int) -> Unit,
    onTaskProgress: (Int, Float, Long) -> Unit,
    onTaskVerifying: (Int) -> Unit,
    onTaskComplete: (Int) -> Unit,
    onTaskFailed: (Int, String) -> Unit,
    onSyncComplete: () -> Unit,
    onError: (String) -> Unit
) {
    Logger.i("Sync", "开始同步（预览模式）- ${previewItems.size} 个文件")

    val port = nasPort.toIntOrNull() ?: 445

    try {
        // 保存配置到 DataStore
        settingsDataStore.saveNasConfig(
            NasConfig(
                host = nasHost,
                port = port,
                shareName = shareName,
                username = username,
                password = password,
                remotePath = ""
            )
        )

        // 建立 SMB 连接
        val connected = smbManager.connect(
            host = nasHost,
            port = port,
            username = username,
            password = password,
            shareName = shareName
        )

        if (!connected) {
            onError("无法连接到 NAS，请检查网络和凭据")
            onDisconnected()
            return
        }

        onConnected()

        // 构建任务列表
        val tasks = previewItems.map { item ->
            TransferTask(
                fileName = item.fileName,
                localSize = item.localSize,
                remoteSize = item.remoteSize,
                status = TaskStatus.PENDING
            )
        }
        onTaskListReady(tasks)

        // 逐个处理任务
        for ((index, item) in previewItems.withIndex()) {
            val localPath = item.localPath
            val remotePath = item.remotePath
            val targetPath = "${localPath.substringBeforeLast('/')}/${item.fileName}.${remotePath.substringAfterLast('.')}"

            onTaskStart(index)
            Logger.i("Sync", "开始处理任务 [$index]: ${item.fileName}")

            try {
                // 检查磁盘空间
                val availableSpace = fileRepository.getAvailableSpace()
                if (availableSpace < item.remoteSize) {
                    Logger.w("Sync", "磁盘空间不足: 需要 ${item.remoteSize}B, 可用 ${availableSpace}B")
                    onTaskFailed(index, "磁盘空间不足")
                    continue
                }

                // 下载文件到临时路径
                val tempPath = "$targetPath.tmp"
                val tempFile = java.io.File(tempPath)
                val outputStream = tempFile.outputStream()

                Logger.i("Sync", "开始下载: $remotePath -> $tempPath")

                val downloadSuccess = smbManager.downloadFile(
                    remotePath = remotePath,
                    outputStream = outputStream,
                    onProgress = { downloaded, total ->
                        val progress = if (total > 0) {
                            (downloaded.toFloat() / total * 100)
                        } else 0f
                        onTaskProgress(index, progress, downloaded)
                    }
                )
                outputStream.close()

                if (!downloadSuccess) {
                    Logger.e("Sync", "下载失败: ${item.fileName}")
                    tempFile.delete()
                    onTaskFailed(index, "下载失败")
                    continue
                }

                Logger.i("Sync", "下载完成: ${item.fileName}")

                // 防丢校验 - 严格比对文件大小
                onTaskVerifying(index)

                val localDownloadedSize = tempFile.length()
                val remoteFileSize = item.remoteSize

                Logger.d("Sync", "开始完整性校验: 远端=${remoteFileSize}B, 本地=${localDownloadedSize}B")

                if (localDownloadedSize != remoteFileSize) {
                    Logger.e("Sync", "完整性校验失败! 文件大小不一致: ${item.fileName}")
                    tempFile.delete()
                    onTaskFailed(index, "校验失败：远端${remoteFileSize}B，本地${localDownloadedSize}B")
                    continue
                }

                Logger.i("Sync", "完整性校验通过: ${item.fileName}")

                // 校验通过，重命名临时文件为目标文件
                val targetFile = java.io.File(targetPath)
                if (targetFile.exists()) targetFile.delete()
                val renameSuccess = tempFile.renameTo(targetFile)
                if (!renameSuccess) {
                    Logger.e("Sync", "文件重命名失败: $targetPath")
                    tempFile.delete()
                    onTaskFailed(index, "文件重命名失败")
                    continue
                }

                // 删除原视频
                val localVideoInfo = FileRepository.LocalVideoInfo(
                    name = item.fileName,
                    extension = localPath.substringAfterLast('.'),
                    path = localPath,
                    size = item.localSize,
                    uri = null
                )
                val deleteSuccess = fileRepository.deleteLocalVideo(localVideoInfo)
                if (!deleteSuccess) {
                    Logger.w("Sync", "原视频删除失败: $localPath")
                } else {
                    Logger.i("Sync", "原视频已删除: $localPath")
                }

                // 触发媒体库扫描
                fileRepository.triggerMediaScan(targetPath)
                Logger.i("Sync", "任务完成: ${item.fileName}")

                onTaskComplete(index)

            } catch (e: Exception) {
                Logger.e("Sync", "处理任务异常: ${item.fileName}", e)
                e.printStackTrace()
                val tempFile = java.io.File("$targetPath.tmp")
                if (tempFile.exists()) tempFile.delete()
                onTaskFailed(index, e.message ?: "未知错误")
            }
        }

        // 同步完成
        val completedCount = tasks.count { it.status == TaskStatus.COMPLETED }
        Logger.i("Sync", "同步流程完成: 成功 $completedCount/${tasks.size}")
        snackbarHostState.showSnackbar("同步完成，成功处理 $completedCount/${tasks.size} 个文件")

    } catch (e: Exception) {
        Logger.e("Sync", "同步过程异常", e)
        onError("同步过程出错：${e.message}")
    } finally {
        smbManager.disconnect()
        onDisconnected()
        onSyncComplete()
        Logger.i("Sync", "同步流程结束，连接已断开")
    }
}
