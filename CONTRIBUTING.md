# 贡献指南

感谢您对 Origin_Mobile - kqChecker 项目的关注和支持！本指南将帮助您了解如何有效地为项目做出贡献。

## 目录
- [贡献方式](#贡献方式)
- [开发环境设置](#开发环境设置)
- [代码规范](#代码规范)
- [分支策略](#分支策略)
- [提交流程](#提交流程)
- [问题报告](#问题报告)
- [Pull Request 规范](#pull-request-规范)
- [审查流程](#审查流程)
- [行为准则](#行为准则)

## 贡献方式

您可以通过以下方式为项目做出贡献：

- 修复 bug
- 添加新功能
- 改进文档
- 优化性能
- 报告问题或提出建议
- 参与代码审查

## 开发环境设置

### 准备工作

1. **安装必要软件**
   - Android Studio Hedgehog 或更高版本
   - Git 版本控制系统
   - JDK 17

2. **克隆仓库**
   ```bash
   git clone https://github.com/yourusername/Origin_Mobile.git
   cd Origin_Mobile
   ```

3. **设置开发环境**
   - 在 Android Studio 中打开项目
   - 等待 Gradle 同步完成
   - 确保能够成功构建项目
   ```bash
   ./gradlew assembleDebug
   ```

## 代码规范

为了保持代码的一致性和可维护性，请遵循以下规范：

### Kotlin 代码规范

- 遵循 [Kotlin 官方编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- 使用 2 个空格进行缩进
- 类名使用 PascalCase（首字母大写）
- 函数名和变量名使用 camelCase（首字母小写）
- 常量名使用全大写加下划线
- 每行代码不超过 100 个字符
- 使用挂起函数（suspend functions）处理异步操作
- 适当添加注释，但避免冗余注释

### Jetpack Compose 规范

- 组件命名使用 PascalCase
- 组合函数参数使用 camelCase
- 避免在组合函数中执行耗时操作
- 使用适当的重组策略（remember, derivedStateOf 等）
- 组件内部逻辑清晰，遵循单一职责原则

### 架构规范

- 遵循 MVVM 架构模式
- 使用依赖注入管理组件依赖
- 关注点分离，避免紧耦合
- 视图层只负责 UI 渲染，业务逻辑放在 ViewModel 或 Repository 中

## 分支策略

- **main**: 主分支，包含稳定版本代码
- **develop**: 开发分支，包含最新开发版本
- **feature/**: 新功能开发分支
- **bugfix/**: bug 修复分支
- **hotfix/**: 紧急修复分支

### 分支命名规范

- `feature/short-description`（例如：`feature/add-sync-worker`）
- `bugfix/issue-number-short-description`（例如：`bugfix/123-fix-crash`）
- `hotfix/description`（例如：`hotfix/fix-auth-token`）

## 提交流程

### 工作流

1. **创建分支**
   ```bash
   git checkout develop
   git checkout -b feature/your-feature-name
   ```

2. **进行开发**
   - 编写代码和测试
   - 确保代码符合规范
   - 解决任何编译错误和警告

3. **提交更改**
   - 使用有意义的提交消息
   - 一个提交应该专注于一个功能或修复
   - 遵循[提交消息规范](#提交消息规范)
   ```bash
   git add .
   git commit -m "feat: 添加新功能描述"
   ```

4. **推送到远程仓库**
   ```bash
   git push origin feature/your-feature-name
   ```

5. **创建 Pull Request**
   - 从您的分支到 `develop` 分支创建 PR
   - 填写 PR 模板，提供详细描述
   - 等待代码审查

### 提交消息规范

提交消息应该简洁明了，遵循以下格式：

```
类型: 简短描述

详细说明（可选）

关闭 #issue-number（如果适用）
```

**类型** 可以是：
- `feat`: 新功能
- `fix`: bug 修复
- `docs`: 文档更新
- `style`: 代码风格修改（不影响功能）
- `refactor`: 代码重构（不改变功能）
- `perf`: 性能优化
- `test`: 测试相关
- `chore`: 构建过程或辅助工具的变动

## 问题报告

如果您发现了 bug 或者有新功能的建议，请提交一个 issue。提交前请搜索现有 issue，确保没有重复。

### Issue 模板

- **标题**: 简洁明了地描述问题或功能请求
- **类型**: 选择 bug、feature、enhancement 或 question
- **描述**: 详细描述问题或建议
- **复现步骤**（如果是 bug）:
  1. 
  2. 
  3. 
- **预期行为**
- **实际行为**
- **环境信息**: Android 版本、设备型号等
- **截图**（如适用）

## Pull Request 规范

提交 PR 时，请遵循以下规范：

1. **标题**: 简洁明了地描述 PR 的内容
2. **描述**: 
   - 解释为什么需要这个 PR
   - 描述所做的更改
   - 参考相关 issue
3. **检查清单**:
   - [ ] 代码符合项目的编码规范
   - [ ] 所有测试都已通过
   - [ ] 没有引入新的警告
   - [ ] 文档已更新（如需要）

## 审查流程

1. **初步审查**: 维护者会检查 PR 是否符合项目要求
2. **代码审查**: 至少一名维护者会审查代码质量和实现
3. **反馈**: 审查者会提供反馈，您需要根据反馈进行修改
4. **合并**: 所有审查通过后，维护者会合并 PR

### 审查标准

- 代码质量和可读性
- 功能正确性
- 性能和资源使用
- 与项目架构的一致性
- 测试覆盖率

## 行为准则

参与本项目时，请遵守以下行为准则：

- 尊重他人，使用包容的语言
- 接受建设性批评
- 专注于对项目最有利的事情
- 对其他社区成员表示同理心

## 联系方式

如有任何问题或建议，请通过以下方式联系我们：

- GitHub Issues
- 邮件：[harcourtzzz@outlook.com]

---

感谢您的贡献！您的参与对于项目的成功至关重要。