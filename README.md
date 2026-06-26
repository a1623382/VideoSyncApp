# 视频同步助手 (VideoSyncApp)

一款 Android 端视频同步与替换应用，通过 SMB 协议连接局域网 NAS，自动匹配并下载高画质转码视频替换手机中的原始视频。

## 功能特性

### 核心功能
- **智能匹配**：根据视频编码自动选择匹配策略
  - H.264/MPEG-4 等编码 → 匹配 NAS 上的 MKV 文件（转码后的高画质版本）
  - HEVC/VP9/AV1 编码 → 匹配原格式文件
- **目录相似性匹配**：优先匹配目录结构相似的同名文件，避免错误替换
- **边删边下模式**：先删除原视频释放空间，再下载新视频，节省手机存储
- **文件完整性校验**：下载完成后严格比对文件大小，确保无损替换

### 界面设计
- **Material Design 3**：全面采用 MD3 设计规范，支持动态取色
- **全屏预览界面**：同步前显示匹配文件列表，支持搜索过滤和选择
- **左右对照表格**：清晰对比本地路径和远端路径
- **点击路径查看详情**：点击路径区域可查看完整路径
- **实时进度显示**：总进度和当前文件进度双重展示

### 实用功能
- **暂停/继续同步**：支持暂停后继续，剩余任务不会丢失
- **已处理文件记录**：自动记录已同步文件，避免二次处理
- **远端目录浏览器**：输入路径时可浏览 NAS 目录结构
- **日志导出**：导出详细日志用于离线调试
- **电池优化检测**：引导用户关闭电池优化，确保后台同步稳定
- **凭据加密存储**：使用 Android KeyStore 加密保存 NAS 密码

## 技术架构

### 技术栈
- **语言**：Kotlin
- **UI 框架**：Jetpack Compose + Material Design 3
- **网络通信**：SMBJ 库通过 SMB 协议连接 NAS
- **异步机制**：Kotlin Coroutines
- **本地存储**：Preferences DataStore + Android KeyStore

### 项目结构
```
app/src/main/java/com/videosync/app/
├── MainActivity.kt                    # 主 Activity 入口
├── VideoSyncApplication.kt            # Application 类
├── data/
│   ├── FileRepository.kt              # 文件扫描、权限、匹配逻辑
│   ├── ProcessedFilesManager.kt       # 已处理文件记录管理
│   ├── SettingsDataStore.kt           # NAS 配置持久化
│   └── SmbManager.kt                  # SMB 连接与文件操作
├── ui/
│   ├── screens/
│   │   └── MainScreen.kt             # 主界面 Compose UI
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── util/
    ├── BatteryOptimizationHelper.kt   # 电池优化检测
    ├── CryptoManager.kt               # 加密管理
    ├── Logger.kt                      # 日志系统
    └── VideoCodecHelper.kt            # 视频编码检测
```

## 权限说明

| 权限 | 用途 |
|------|------|
| INTERNET | SMB 局域网连接 |
| ACCESS_NETWORK_STATE | 检测网络状态 |
| MANAGE_EXTERNAL_STORAGE | 访问所有视频文件 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 关闭电池优化 |

## 构建与运行

### 环境要求
- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34

### 构建步骤
```bash
# 克隆仓库
git clone https://github.com/a1623382/VideoSyncApp.git

# 进入项目目录
cd VideoSyncApp

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

### GitHub Actions CI
项目配置了 GitHub Actions 自动构建，推送到 main 分支时自动触发：
- 构建 Debug 和 Release APK
- 签名 Release APK
- 上传 APK 作为 Artifact

## 使用说明

1. **配置 NAS 连接**
   - 填写服务器地址、端口、共享文件夹名称
   - 填写用户名和密码（会加密存储）
   - 填写远端子目录路径（可使用目录浏览器）

2. **开始同步**
   - 点击"开始同步"按钮
   - 等待扫描匹配完成
   - 在预览界面选择要同步的文件
   - 确认后开始下载替换

3. **同步过程中**
   - 可随时暂停或停止
   - 查看实时进度和文件大小对比
   - 导出日志用于调试

## 日志导出

点击右上角分享按钮可导出日志文件，保存位置：
```
/Documents/VideoSync/videosync_log_YYYYMMDD_HHmmss.txt
```

## 版本历史

### v1.2.0
- 版本升级

### v1.1.0
- 添加已处理文件记录，避免二次处理
- 智能匹配逻辑：根据编码选择匹配策略
- 目录相似性匹配
- 视频编码检测
- 代码优化和清理

### v1.0.0
- 初始版本
- SMB 连接和文件同步
- Material Design 3 界面
- 日志导出功能

## 许可证

本项目仅供个人学习使用。

## 项目地址

https://github.com/a1623382/VideoSyncApp
