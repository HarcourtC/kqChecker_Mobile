# Contributing to kqChecker

<p align="center">
  <img src="https://img.shields.io/badge/Welcome-Contribute-blue?style=for-the-badge" alt="Welcome">
</p>

感谢您对 kqChecker 项目的关注！本指南将帮助您快速上手并有效地贡献代码。

---

## 🚀 快速开始

```bash
# 1. Fork & Clone
git clone https://github.com/YOUR_USERNAME/kqChecker_Mobile.git
cd kqChecker_Mobile

# 2. 创建开发分支
git checkout -b feat/your-feature

# 3. 开发 & 测试
./gradlew assembleDebug

# 4. 提交 & 推送
git add .
git commit -m "feat: 添加新功能"
git push origin feat/your-feature
```

---

## 📋 开发规范

### 分支命名

```
feat/xxx     → 新功能
fix/xxx      → Bug 修复
refactor/xxx → 重构
docs/xxx    → 文档更新
chore/xxx    → 构建/工具
```

### 提交信息

```
<type>(<scope>): <subject>

<body>

<footer>
```

| Type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档更新 |
| `style` | 代码格式 |
| `refactor` | 重构 |
| `perf` | 性能优化 |
| `chore` | 构建/工具 |

**示例：**
```bash
git commit -m "feat(repository): 添加数据缓存机制

- 实现基于 JSON 文件的缓存
- 添加缓存过期检查
- 优化网络请求频率

Closes #12"
```

### 代码规范

| 规则 | 说明 |
|------|------|
| 缩进 | 2 空格 |
| 类名 | PascalCase |
| 函数/变量 | camelCase |
| 常量 | UPPER_SNAKE_CASE |
| 行宽 | ≤ 100 字符 |

**检查命令：**
```bash
# 代码格式检查
./gradlew ktlintCheck

# 自动修复
./gradlew ktlintFormat
```

---

## 🏗️ 架构指南

```
┌────────────────────────────────────────────┐
│                  UI Layer                   │
│  Compose Screens → ViewModel → StateFlow   │
└────────────────────────────────────────────┘
                      │
                      ▼
┌────────────────────────────────────────────┐
│               UseCase Layer                │
│         业务逻辑编排与流程控制               │
└────────────────────────────────────────────┘
                      │
                      ▼
┌────────────────────────────────────────────┐
│              Repository Layer               │
│       数据获取 │ 缓存管理 │ API 调用        │
└────────────────────────────────────────────┘
                      │
                      ▼
┌────────────────────────────────────────────┐
│               Network Layer                 │
│         Retrofit + OkHttp + Moshi          │
└────────────────────────────────────────────┘
```

### 模块职责

| 模块 | 职责 |
|------|------|
| `ui/` | Compose 界面与状态管理 |
| `domain/usecase/` | 业务逻辑编排 |
| `repository/` | 数据访问与缓存 |
| `network/` | API 请求 |
| `auth/` | 身份验证与 Token |
| `sync/` | 后台任务与调度 |
| `util/` | 通用工具方法 |

---

## 🔧 Pull Request 流程

### 1. 创建分支

```bash
# 从 dev 分支创建
git checkout dev
git pull origin dev
git checkout -b feat/your-feature
```

### 2. 开发与提交

- ✅ 编写清晰的代码注释
- ✅ 添加必要的单元测试
- ✅ 运行 `./gradlew assembleDebug` 确保编译通过
- ✅ 运行 `./gradlew ktlintCheck` 确保代码规范

### 3. 提交 PR

```bash
git push origin feat/your-feature
```

然后在 GitHub 上创建 Pull Request 到 `dev` 分支。

### PR 模板

```markdown
## 概述
<!-- 简要说明这个 PR 做了什么 -->

## 改动内容
<!-- 详细描述改动点 -->

## 测试
- [ ] 本地测试通过
- [ ] 模拟器/真机测试通过

## 截图 (如有 UI 改动)
<!-- 添加截图 -->

## 相关 Issue
Closes #xxx
```

---

## 🐛 问题反馈

### Issue 模板

```markdown
## 问题描述
<!-- 清晰描述遇到的问题 -->

## 复现步骤
1. 打开应用
2. 执行xxx操作
3. 出现xxx错误

## 预期行为
<!-- 期望的结果 -->

## 实际行为
<!-- 实际的结果 -->

## 环境信息
- 设备型号：
- Android 版本：
- App 版本：
```

---

## 📞 联系方式

- 📧 邮箱：harcourtzzz@outlook.com
- 💬 GitHub Issues

---

<p align="center">
  感谢您的贡献！🎉
</p>
