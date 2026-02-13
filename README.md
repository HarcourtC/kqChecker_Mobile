# Origin_Mobile - kqChecker 移动应用

## 项目简介

Origin_Mobile 是一个基于 Android Kotlin 和 Jetpack Compose 开发的考勤检查（kqChecker）移动应用，提供了完整的网络请求、数据同步和用户交互功能。

## 技术栈

- **开发语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **网络请求**: Retrofit + Moshi
- **数据存储**: SharedPreferences
- **后台任务**: WorkManager
- **协程处理**: Kotlin Coroutines
- **架构模式**: MVVM (Model-View-ViewModel)

## 项目结构

```
Origin_Mobile/
├── app/
│   ├── build.gradle         # 应用模块构建配置
│   └── src/main/java/org/example/kqchecker/
│       ├── MainActivity.kt  # 主活动入口
│       ├── NetworkModule.kt # 网络请求模块
│       ├── TokenStore.kt    # Token 存储管理
│       ├── SyncWorker.kt    # 后台同步工作器
│       └── ...              # 其他功能模块
├── build.gradle             # 项目级构建配置
├── gradle/                  # Gradle 包装器
├── settings.gradle          # Gradle 设置
├── README.md                # 项目说明文档
└── CONTRIBUTING.md          # 贡献指南
```

## 主要功能

### 核心功能
- 考勤数据同步与检查
- 竞赛信息获取与本地缓存（支持从 https://api.harco.top/xjtudean 获取竞赛数据）
- 网络请求与响应处理
- 数据缓存管理
- 事件日志记录与显示
- JSON 数据导出功能
- 运行时调试入口（打印后端原始响应、分块写入 Logcat），便于复制和离线分析

### 技术特性
- Compose UI 组件构建
- 协程异步操作处理
- 网络请求重试机制
- DNS 解析与请求头管理
- 本地文件存储与读取

## 安装与运行

### 环境要求
- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34（Target SDK）
- Gradle 8.0+

### 安装步骤
1. 克隆或下载项目到本地
2. 在 Android Studio 中打开项目根目录（选择 `Origin_Mobile`）
3. 等待 Gradle 同步完成（可能需要安装必要的插件）
4. 在 `NetworkModule` 中配置后端 API 地址

### 运行项目
1. 连接 Android 设备或启动模拟器
2. 点击 "Run 'app'" 按钮或使用 Shift+F10 快捷键
3. 应用将安装并启动到设备上

## 配置说明

### 网络配置
- 在 `NetworkModule` 类中修改 `BASE_URL` 常量，指向你的后端服务地址
- 根据需要调整 `OkHttpClient` 的超时设置和拦截器配置

### 权限配置
应用需要以下权限，请确保在 AndroidManifest.xml 中正确配置：
- 网络访问权限
- 存储访问权限（用于导出数据）

## 使用说明

### 登录功能
应用启动时会自动检查缓存并尝试登录。成功登录后，Token 将被保存在 `TokenStore` 中供后续请求使用。

### 数据同步
- 使用界面上的 "Trigger Sync" 按钮手动触发数据同步
- "Run Experimental Sync" 按钮提供高级同步功能
- 后台通过 `SyncWorker` 定期执行数据同步任务

### 数据导出
使用 "Export weekly.json to Downloads" 按钮可以将周数据导出到设备的 Downloads 目录。

### 日志查看
应用界面底部的日志区域会显示所有操作事件和错误信息，方便调试和监控。

### 竞赛数据获取
应用支持获取竞赛信息（来自 https://api.harco.top/xjtudean），并自动缓存到本地：
- 首次加载从 API 获取数据并缓存
- 后续加载优先从本地缓存读取
- 支持强制刷新跳过缓存直接从 API 获取
- 缓存文件：`competition_data.json`

详见 [COMPETITION_FEATURE_GUIDE.md](COMPETITION_FEATURE_GUIDE.md) 获取详细使用说明。

## 开发指南

### 代码规范
- 遵循 Kotlin 语言规范和 Jetpack Compose 最佳实践
- 使用挂起函数（suspend functions）处理异步操作
- 采用依赖注入模式管理组件依赖

### 调试工具
- 使用 "Debug Request" 按钮可以测试和调试网络请求
- 界面提供缓存状态检查和日志显示功能

详细的 adb 调试/复现命令（清理缓存、拉取缓存文件、抓取过滤后的 Logcat）以及新加的工具按钮使用说明见 `docs/运行指南.md` 和 `docs/API说明.md`。

## 贡献指南

请参考 [CONTRIBUTING.md](CONTRIBUTING.md) 文件了解如何为项目贡献代码。

## 许可证

该项目采用 MIT 许可证 - 详情请查看 LICENSE 文件

## 联系方式

如有任何问题或建议，请通过以下方式联系我们：
- 技术支持：[harcourtzzz@outlook.com]

