# 参与贡献与开发者指南 (Contributing & Developer Guide)

<p align="center">
  <img src="https://img.shields.io/badge/Welcome-Contribute-blue?style=for-the-badge" alt="Welcome">
  <img src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat" alt="PRs Welcome">
</p>

感谢您对 **kqChecker** (Origin_Mobile) 项目代码仓库的关注！本指南不仅协助您完成开发环境的初步搭建，还深度整合了本应用的**核心架构选型、数据流向以及本地缓存行为规则**，将帮助您快速融贯项目的全貌并有效率地贡献高质量代码。

---

## 🛠 核心技术栈与 SDK 规范

- **开发语言**: Kotlin 1.9.10
- **UI 框架**: Jetpack Compose 1.5.3 (现代声明式 UI 开发)
- **构建工具**: Android Gradle Plugin 8.13.0
- **网络层**: Retrofit 2.9.0 + OkHttp 4.11.0
- **异步与任务调度**: Kotlin Coroutines, WorkManager 2.8.1 (配合后台轮询 Service)
- **安全与存储**: EncryptedSharedPreferences (保障各类隐私凭据)
- **架构模式**: 基于 Clean Architecture 的实体演进 (MVVM + Repository + UseCase 业务编排)

> **Android 构建环境基线配置**:
> - `compileSdk = 36`
> - `targetSdk = 36`
> - `minSdk = 21` (兼容 Android 5.0)

---

## 🚀 快速开始 (本地克隆、编译与运行)

### 1. 准备并拉取源码

```bash
# 复制仓库到本地（或者 fork 出你自己的仓库地址进行 clone）
git clone https://github.com/HarcourtC/kqChecker_Mobile.git
cd kqChecker_Mobile
```

### 2. 构建工程

```bash
# 基于 Linux 或 macOS
./gradlew assembleDebug

# Windows PowerShell 终端下：
.\gradlew.bat assembleDebug
```

### 3. 安装部署至设备

使用 ADB 安装打好的 Debug 安装包：
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或者使用 Gradle 直接部署：
```bash
./gradlew installDebug
```

> **备注**：关于如何使用 Android Studio 或者在没有 IDE 时处理真机调试以及如何从 ADB 截获并过滤专有日志，推荐深入阅读 [安卓真机运行程序指南](./docs/运行指南.md)。

---

## 🏗️ 架构概览与代码目录结构

项目源码遵循清晰的分层策略隔离职责边界。核心代码树位于 `app/src/main/java/org/xjtuai/kqchecker/` 下：

```text
┌────────────────────────────────────────────┐
│                  UI Layer                  │  -> ui/ (Compose 页面布局、各类自定义组件与状态持有者 ViewModel)
│  Compose Screens → ViewModel → StateFlow   │
└────────────────────────────────────────────┘
                      │
                      ▼
┌────────────────────────────────────────────┐
│               UseCase Layer                │  -> domain/usecase/ (调度集成流程：例如拉课表->清洗加工->写入日历)
│         业务逻辑聚合与复杂控制流             │
└────────────────────────────────────────────┘
                      │
                      ▼
┌────────────────────────────────────────────┐
│              Repository Layer              │  -> repository/ (向上屏蔽缓存读取与 API 发起的异同)
│       数据仓库统筹 │ 缓存拦截与回退管理       │
└────────────────────────────────────────────┘
                      │
                      ▼
┌────────────────────────────────────────────┐
│               Network Layer & Base         │  -> network/, sync/, auth/, model/ (底层实体支撑)
│  网络请求接口映射、授权拦截器、后台 Worker    │
└────────────────────────────────────────────┘
```

### 主要 Module 职能说明：
- `auth/`：集中式 WebView 登录拦截、统一身份验证（Token）信息的持久化持有。
- `network/`：网络底层客户端装配。
- `repository/`：
  - `WeeklyRepository`：专职代理拉取周课表配置，负责处理过期失效和缓存写库逻辑。
  - `WaterListRepository`：代理请求考勤流水（API2接口）。
  - `CompetitionRepository`：代理请求并缓存竞赛列表。
- `sync/`：后台同步载体，包含诸如 `Api2AttendanceQueryWorker`（考勤后台查询）、`CompetitionDeadlineWorker`（每日竞赛提醒检查）与负责日程集成的类组件。
- `util/`：杂项工具箱如 `WeeklyCleaner` 用于 JSON 解析扁平化。

---

## 💾 数据流、本地缓存生成与系统集成方式

整个应用业务主线被设计为在**内网隔离与 Token 短命特征下的韧性存取**。

应用的各种关键业务结果会写入系统给定的 `context.filesDir` (例如 `/data/data/.../files/` 目录下)：
- `weekly.json`：带有自定义标记状态的课表主数据文件。
- `weekly_raw.json` & `weekly_raw_meta.json`：保留了来自接口最原始、且带有抓取日期等 meta 标签痕迹的原始文件体。
- `api2_waterlist_response.json`：最新批次的考勤记录留底。
- `competition_data.json`：全量竞赛数据的脱机备份。

**特别指出关于日历集成的工作流**：
不论是课表还是临时增加的作业条目，其最终展现方式依赖于将清理整理过的结构 (`weekly_cleaned.json`) 透过 Android 原生的 `ContentResolver` 与 `CalendarContract` 系统 API 逐一注入用户本地的系统日历中。对于已经写入的日程条目，应用采用了特征标识比对机制来决定是覆盖刷新还是忽略执行。

---

## ⚙️ 私有接口与联调配置的覆写说明 (Config Setup)

所有网络请求所需的 Base Url、各个 Endpoint 后缀或验证用的鉴权参数，都在 `app/src/main/assets/config.json` 中统一管理。然而，由于 GitHub 公开仓库的脱敏策略，代码库里的默认配置填写的全是掩码（如 `https://example.invalid/`）。

为了令本应用在本地工作流能够实际跑通，你必须在此路径下手工创建一份 **`app/src/main/assets/config.private.json`**：

```json
{
  "base_url": "真实的业务后端地址",
  "auth_login_url": "真实的 SSO 登录链接",
  "auth_redirect_prefix": "用来做 WebView url 匹配前缀的地址",
  "competition_api_key": "REPLACE_WITH_YOUR_PRIVATE_KEY"
}
```
**说明**：底层 `ConfigHelper` 启动时会自动利用此 `*.private.json` 全盘覆盖 `config.json` 当中暴露公开的字段，而且这一文件由于被 `.gitignore` 排除将绝不被泄露入库。

---

## 📋 提交规范与 PR 管理流

### Git 分支指引
建议直接派生（Fork）此仓库作为起点，当完成一项特性开发时：
- `feat/feature-name` → 完整的新功能引入
- `fix/issue-description` → 指向特定缺陷的漏洞修复
- `refactor/target-module` → 代码内部结构与坏味道的重构（不改变现有逻辑结果）
- `docs/updating-readme` → 文档类的改动

### 提交格式要求
```text
feat(usecase): 增加作业图片同步上传与识别能力

- 新增了相机调用与压缩工具类
- 修改了日历集成时针对附图的支持

Closes #29
```

### 规范检查器 (Ktlint) 拦截检查
你必须在每次发起 PR 提交前调用 Gradle 的检查项：
```bash
# 执行校验是否违背缩进、换行或命名约定（PascalCase / camelCase 等）
./gradlew ktlintCheck

# 委托 Gradle 直接为您完成基础代码的自动美化排版
./gradlew ktlintFormat
```

### 🐛 Issue与讨论
如果遇到难以通过日志排查清楚的问题（例如接口频频 403 阻断），您随时可以通过 GitHub Issues 在对应的分类下开新贴进行讨论并提供复现日志片段。感谢您的热心合作与无偿贡献！
