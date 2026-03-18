<h1 align="center">kqChecker</h1>

<p align="center">
  <img src="./icon.png" width="128" alt="kqChecker logo">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform Android">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=android&logoColor=white" alt="Compose">
  <a href="https://github.com/HarcourtC/kqChecker_Mobile/releases">
    <img src="https://img.shields.io/github/v/release/HarcourtC/kqChecker_Mobile?style=for-the-badge&color=007EC6&logo=github" alt="Latest Release">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Architecture-MVVM_%7C_Clean-FF8A65?style=for-the-badge" alt="Architecture">
  <img src="https://img.shields.io/badge/Min_SDK-21-00BCD4?style=for-the-badge" alt="Min SDK">
  <a href="./LICENSE">
    <img src="https://img.shields.io/badge/License-All_Rights_Reserved-E53935?style=for-the-badge" alt="License">
  </a>
</p>

**kqChecker** 是一款为高校学生打造的考勤与课表辅助 Android 应用。它能够帮助你轻松管理日常课程、自动同步考勤数据、查询最新竞赛信息，并支持将课程一键导入系统日历，实现系统级提醒。



## ✨ 核心特性

- 📅 **智能课表管理**：支持一键拉取周课表，离线缓冲；可将课程与作业一键写入手机系统日历，享受原生系统级日程提醒。
- 📝 **作业随手记**：在课表视图中支持手动作业记录，甚至一键上传作业图片，不再遗漏任何重要验收点。
- 🔍 **后台考勤聚合**：自动在后台查询与对比考勤流水数据（基于 API2），助你随时掌控当前出勤状态，异常缺勤早知道。
- 🏆 **竞赛资讯速递**：接入竞赛数据源，支持按类别筛选、只看最近内容、站内直达详情，并每天定时提醒竞赛截止日期。
- 🛠️ **贴心辅助工具**：内置丰富的数据缓存预览、手动清理账号过期信息、控制后台同步等便捷操作面板。

## 📥 下载与安装

### 获取应用
前往本仓库的 [Releases 页面](https://github.com/HarcourtC/kqChecker_Mobile/releases)，下载最新版本的 `app-release.apk`。

> [!NOTE]
> **XJTU 专用说明**：Release 页面发布的 APK 已预先注入了西交大专用的 `config.private.json` 配置，**西安交大学生下载后即可直接登录使用**。其他高校如需适配，请联系作者或参照开发者文档自行编译。

### 安装要求
- **Android 系统版本**：Android 5.0 (API 21) 及以上。
- 安装时，请在手机系统设置中允许“来自未知来源的应用”的安装权限。

## 📖 快速上手

1. **授权必要权限**
   首次打开应用时，请根据屏幕提示授予**网络访问**与**日历读写**权限。如果需要直接将课程导入至系统日历，日历权限是极其必要的。

2. **账号登录与绑定**
   点击应用首页的“登录”入口，在内置的安全 WebView 容器中完成统一身份认证登录。登录凭证将自动加密保存在本地（您随时可以在工具页清理账号数据）。

3. **同步课表与数据刷新**
   登录成功后，应用一般会自动拉取本周的课表并进行自动缓存。你也可以随时在页面中下拉或点击首页刷新按钮强制进行手动同步。

4. **一键生成系统日程**
   在“集成”页面或课表页面点击拉取完成后的“一键集成”，应用会将课表数据进行智能去重清洗，然后一并写入您的手机系统原生日历中。

## ❓ 常见问题汇总

- **为什么提示“登录已失效”或拉取失败？**
  鉴权使用的 Token 存在短期有效性限制。如果你过了一段时间再次打开应用遇到拉取失败，应用会自动尝试清理本地失效状态。遇此情况只需点击首页弹出的“重登录”按钮，重新完成一次统一认证即可接续使用。

- **为什么课表没有成功写入日历栏？**
  很大原因是日历权限没完全打开。请前往手机**设置 -> 应用管理 -> kqChecker -> 权限管理**，确保已实际授予“读写日历”的权限，然后回到 App 再次尝试写入。
- **如果有更多的问题**
  请提出issues或邮件联系~
## 👩‍💻 面向开发者

如果你是对本项目感兴趣的技术同学，希望从源码编译、了解项目内部技术栈（Kotlin + Jetpack Compose + MVVM 架构）、学习后台 Worker 机制，或希望提交代码修复与新功能贡献，请务必参阅以下配套文档集合：

- [**参与贡献与架构指南**](./CONTRIBUTING.md) 🌟 —— 这是开发者首选的最佳起点，包含应用整体技术栈、模块层级树、缓存运作行为，以及代码规范与提交流程指引。
- [**运行与调试指南**](./docs/运行指南.md) —— 如何进行真机调试、抓取核心业务日志和本地排查清单。
- [**网络 API 协议说明**](./docs/API说明.md) —— 解读教务系统拉取、考勤 API、竞赛接口的通信格式。
- [**竞赛模块设计说明**](./docs/COMPETITION_FEATURE_GUIDE.md) —— 具体剖析竞赛数据源的对接与缓存。
- [**模块拆分设计方案**](./docs/模块拆分方案.md) —— 了解重构后各 package 的职能切分。

## 📄 许可证

本项目当前采用 [All Rights Reserved](./LICENSE)。

> 声明：源码公开仅供学习参考，不代表授予复制、修改、二次分发或任何形式商业使用的许可；如需作他用，请务必先获得著作权人的书面授权。
