# kqChecker

<p align="center">
  <img src="icon.png" width="128" alt="Logo">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen?style=for-the-badge&logo=android" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-blue?style=for-the-badge&logo=kotlin" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-purple?style=for-the-badge&logo=jetpackcompose" alt="UI">
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License">
  <img src="https://img.shields.io/github/v/release/HarcourtC/kqChecker_Mobile?style=for-the-badge" alt="Version">
</p>

> 🚀 西安交通大学考勤检查移动应用 | Attendance Checker Mobile App

## ✨ 特性

| 功能 | 描述 |
|------|------|
| 📅 周课表同步 | 自动获取并缓存周课表数据 |
| 📊 考勤流水 | 查询学生考勤记录详情 |
| 🏆 竞赛信息 | 实时获取教务处竞赛通知 |
| 📅 日历集成 | 课程自动写入系统日历 |
| 🔔 智能提醒 | 考勤异常即时推送通知 |
| 🌙 深色模式 | 完整的 Material 3 深色主题 |

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐   │
│  │ HomeScreen  │ │Competition  │ │  IntegrationScreen  │   │
│  └─────────────┘ └─────────────┘ └─────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    ViewModel Layer                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              MainViewModel                            │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    UseCase Layer                             │
│  ┌─────────────────┐ ┌─────────────────┐ ┌────────────┐  │
│  │IntegrationFlow  │ │ RefreshWeekly   │ │Api2Query   │  │
│  └─────────────────┘ └─────────────────┘ └────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Repository Layer                          │
│  ┌─────────────┐ ┌─────────────┐ ┌──────────────────┐   │
│  │WeeklyRepo   │ │WaterListRepo│ │ CompetitionRepo  │   │
│  └─────────────┘ └─────────────┘ └──────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     Network Layer                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │     Retrofit + OkHttp + Moshi                       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 技术栈

- **Language**: Kotlin 1.9.10
- **UI**: Jetpack Compose 1.5.3 (Material 3)
- **Network**: Retrofit 2.9.0 + OkHttp 4.11.0 + Moshi
- **Async**: Kotlin Coroutines + Flow
- **Background**: WorkManager 2.8.1
- **Storage**: EncryptedSharedPreferences + File Cache
- **Architecture**: MVVM + Repository + Clean Architecture

## 📁 项目结构

```
app/src/main/java/org/xjtuai/kqchecker/
├── auth/                  # 🔐 认证模块
│   ├── TokenStore.kt
│   ├── TokenManager.kt
│   └── WebLoginActivity.kt
├── network/              # 🌐 网络层
│   ├── ApiService.kt
│   ├── ApiClient.kt
│   └── NetworkModule.kt
├── repository/           # 📦 数据仓库
│   ├── WeeklyRepository.kt
│   ├── WaterListRepository.kt
│   └── CompetitionRepository.kt
├── domain/usecase/       # ⚡ 业务用例
│   ├── IntegrationFlowUseCase.kt
│   └── RefreshWeeklyUseCase.kt
├── ui/                   # 🎨 UI 层
│   ├── HomeScreen.kt
│   ├── CompetitionScreen.kt
│   └── viewmodel/
├── sync/                 # 🔄 后台同步
│   ├── WriteCalendar.kt
│   └── Api2AttendanceQueryWorker.kt
└── util/                 # 🛠️ 工具类
    ├── ConfigHelper.kt
    └── CalendarHelper.kt
```

## 🚀 快速开始

### 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 17+ |
| Android SDK | 34+ |
| Gradle | 8.0+ |

### 构建

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 配置

在 `assets/config.json` 中配置：

```json
{
  "base_url": "http://your-api-server.com",
  "termNo": 20241,
  "week": 10
}
```

## 📱 功能预览

| 页面 | 功能 |
|------|------|
| **首页** | 登录状态、缓存状态、同步控制 |
| **课表** | 周课表展示，数据刷新 |
| **竞赛** | 分类筛选、截止日期提醒 |
| **集成** | 日历写入、冲突处理 |
| **工具** | 调试面板、缓存管理 |

## 🔧 开发指南

### 代码规范

- 遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- 使用 ktlint 进行代码检查
- 提交前运行 `./gradlew ktlintCheck`

### 分支策略

```
master     ── 稳定版本 ─────────────────────►
  │
dev        ── 开发分支 ────────────────────►
  │
feat/*     ── 新功能 ─────────────────────►
bugfix/*   ── Bug 修复 ─────────────────►
hotfix/*   ── 紧急修复 ─────────────────►
```

### 提交规范

```
feat:     新功能
fix:      Bug 修复
docs:     文档更新
style:    代码格式
refactor: 重构
perf:     性能优化
chore:    构建/工具
```

## 📄 文档

- [运行指南](docs/运行指南.md) - 设备连接与调试
- [API 说明](docs/API说明.md) - 接口文档
- [贡献指南](CONTRIBUTING.md) - 如何参与开发

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feat/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: 添加新功能'`)
4. 推送分支 (`git push origin feat/amazing-feature`)
5. 打开 Pull Request

## 📄 许可证

MIT License - 查看 [LICENSE](LICENSE) 了解详情

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/HarcourtC">Harcourt</a>
</p>
