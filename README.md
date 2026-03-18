# kqChecker / Origin_Mobile

<p align="center">
  <img src="./icon.png" width="128" alt="kqChecker logo">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen?style=flat" alt="Platform Android">
  <img src="https://img.shields.io/badge/Language-Kotlin-blue?style=flat" alt="Language Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat" alt="UI Jetpack Compose">
  <img src="https://img.shields.io/badge/Architecture-MVVM%20%2B%20Repository-orange?style=flat" alt="Architecture MVVM Repository">
</p>

西安交通大学考勤与课表辅助 Android 应用。项目以 Jetpack Compose 构建，围绕“登录 -> 拉取课表/考勤数据 -> 本地缓存 -> 日历集成 -> 后台提醒”这条主流程实现。

## 项目概览

应用当前包含 5 个主要页面：

- 首页：展示下一节课、登录入口、缓存状态。
- 课表：显示周课表，支持手动作业记录、上传作业图片、写入系统日历。
- 竞赛：读取教务处竞赛数据，支持分类筛选、最近内容过滤、站内 WebView 查看详情。
- 工具：提供调试入口、课表/API2 拉取、缓存预览、账号清理等开发辅助能力。
- 集成：控制 API2 前台轮询、竞赛截止日期每日提醒、事件日志面板开关。

项目还包含：

- WebView 登录与 Token 持久化。
- 周课表缓存与过期检查。
- API2 考勤流水查询与后台 Worker。
- 课程与作业写入系统日历。
- 开机后恢复前台轮询服务。
- GitHub Releases 版本更新检查。

## 技术栈

- Kotlin 1.9.10
- Android Gradle Plugin 8.13.0
- Jetpack Compose 1.5.3
- Retrofit 2.9.0 + OkHttp 4.11.0
- Kotlin Coroutines
- WorkManager 2.8.1
- EncryptedSharedPreferences
- MVVM + Repository + UseCase

Android 配置：

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 21`

## 代码结构

`app/src/main/java/org/xjtuai/kqchecker/` 目录下的核心模块：

```text
auth/               登录、Token 管理、请求鉴权
network/            Retrofit 接口、OkHttp 客户端、响应模型
repository/         课表、考勤流水、竞赛、学期、缓存等数据访问
domain/usecase/     集成流程、课表刷新、日历写入、API2 查询
ui/                 Compose 页面、组件、状态与 ViewModel
sync/               Worker、前台服务、开机广播、日历写入
util/               配置、课表解析、通知、版本检查等工具
debug/              调试仓库与测试 Worker
model/              课表、作业、学期等领域模型
```

仓库同时提供：

- `docs/`：运行指南、API 说明、模块拆分方案、竞赛功能说明。
- `tools/`：本地调试和数据处理脚本。
- `docs-AI/`：AI 辅助文档与后续事项。

## 核心数据流

```text
Compose UI
  -> ViewModel
  -> UseCase
  -> Repository
  -> ApiService / CacheManager
  -> Local files / Android system services
```

关键仓库职责：

- `WeeklyRepository`：拉取周课表、写入 `weekly.json` / `weekly_raw.json`、处理缓存过期。
- `WaterListRepository`：查询 API2 考勤流水并缓存到 `api2_waterlist_response.json`。
- `CompetitionRepository`：读取竞赛数据并缓存到 `competition_data.json`。
- `WeeklyCleaner`：把原始课表转换为便于后续集成的清洗结果。

## 本地缓存与集成行为

应用主要在 `context.filesDir` 下写入缓存文件：

- `weekly.json`
- `weekly_raw.json`
- `weekly_raw_meta.json`
- `api2_waterlist_response.json`
- `competition_data.json`

相关后台能力：

- `Api2AttendanceQueryWorker`：基于课表时间窗口发起 API2 查询。
- `CompetitionDeadlineWorker`：每天检查竞赛截止日期。
- `Api2PollingService`：可选的前台轮询服务。
- `WriteCalendar`：向系统日历写入课程与相关事项。

## 环境要求

- JDK 17+
- Android SDK
- Android Studio 或可用的命令行构建环境
- 已安装 `adb`

## 快速开始

### 1. 克隆与构建

```bash
git clone <your-fork-or-repo-url>
cd Origin_Mobile
./gradlew assembleDebug
```

Windows PowerShell 下也可直接执行：

```powershell
.\gradlew.bat assembleDebug
```

### 2. 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以使用：

```bash
./gradlew installDebug
```

### 3. 运行应用

- 首次打开后按提示授予网络、日历等相关权限。
- 需要访问受保护接口时，先通过 WebView 登录。
- 首页会在启动时检查 `weekly.json` 是否过期，并在需要时自动刷新。

## 配置说明

运行时配置位于 `app/src/main/assets/config.json`。仓库中提交的是脱敏示例值；真实值建议放在本地未提交的 `app/src/main/assets/config.private.json` 中覆盖。

```json
{
  "base_url": "https://example.invalid/",
  "auth_login_url": "https://example.invalid/login",
  "auth_redirect_prefix": "https://example.invalid/app/#/home",
  "weekly_endpoint": "api/schedule/weekly",
  "water_list_endpoint": "api/attendance/water-list",
  "current_term_endpoint": "api/term/current",
  "competition_base_url": "https://example.invalid/",
  "competition_endpoint": "api/competition",
  "competition_api_key": "REPLACE_WITH_PRIVATE_KEY"
}
```

`ConfigHelper` 支持的字段包括：

- `base_url`
- `auth_login_url`
- `auth_redirect_prefix`
- `weekly_endpoint`
- `water_list_endpoint`
- `current_term_endpoint`
- `competition_base_url`
- `competition_endpoint`
- `competition_api_key`
- `termNo`
- `week`

说明：

- `base_url` 最好以 `/` 结尾。
- `termNo` 和 `week` 可作为兜底值参与课表请求构造。
- 默认基础地址也会写入 `BuildConfig.BASE_URL`，公开仓库里使用的是占位值。
- `config.private.json` 中的字段会覆盖 `config.json`，适合保存本地私有接口配置。

## 常用开发命令

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew ktlintCheck
./gradlew ktlintFormat
```

查看设备：

```bash
adb devices
```

查看日志示例：

```bash
adb logcat -v time WeeklyRepository:* MainActivity:* *:S
adb logcat -v time CompetitionRepository:* CompetitionApiInterceptor:* *:S
```

## 调试提示

- 工具页可以直接触发课表刷新、API2 拉取、原始响应预览和缓存输出。
- 课表页支持手工作业记录，并可将作业写入系统日历。
- 竞赛页会优先走缓存，手动刷新时再请求远端 API。
- 登录失效时，仓库层会清理本地 Token 并要求重新登录。

## 相关文档

- [运行指南](./docs/运行指南.md)
- [API 说明](./docs/API说明.md)
- [竞赛功能说明](./docs/COMPETITION_FEATURE_GUIDE.md)
- [模块拆分方案](./docs/模块拆分方案.md)
- [贡献指南](./CONTRIBUTING.md)

## 许可证

本仓库当前采用 [All Rights Reserved](./LICENSE)。

源码公开不代表授予复制、修改、分发或商用许可；如需使用，请先获得著作权人书面授权。

## 当前 README 已修正的仓库事实

- 仓库名是 `Origin_Mobile`，应用名是 `kqChecker`。
- 项目确实使用 Compose、WorkManager 和 WebView 登录。
- 仓库现在已补充项目级 `All Rights Reserved` 许可证声明。
- README 内容已按源码中的页面、缓存文件名、配置字段和主要 Worker 重新整理。
