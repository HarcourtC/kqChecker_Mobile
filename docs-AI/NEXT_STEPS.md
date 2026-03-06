# 下一阶段任务计划

## 背景

已完成 `feat/code-style` 分支的初始工作：
- ✅ ktlint 集成
- ✅ lint 配置增强
- ✅ 部分 UI 文件添加中文注释

## 待完成

### 1. 统一 Repository 层注释为中文

将以下文件的注释统一为中文：

| 文件 | 路径 |
|------|------|
| WeeklyRepository | `repository/WeeklyRepository.kt` |
| WaterListRepository | `repository/WaterListRepository.kt` |
| CacheManager | `repository/CacheManager.kt` |
| WeeklyCleaner | `repository/WeeklyCleaner.kt` |

### 2. 统一 Util 层注释为中文

| 文件 | 路径 |
|------|------|
| ConfigHelper | `util/ConfigHelper.kt` |
| CalendarHelper | `util/CalendarHelper.kt` |
| LoginHelper | `util/LoginHelper.kt` |
| WorkManagerHelper | `util/WorkManagerHelper.kt` |

### 3. 统一 Network 层注释

| 文件 | 路径 |
|------|------|
| NetworkModule | `network/NetworkModule.kt` |
| ApiClient | `network/ApiClient.kt` |
| ApiService | `network/ApiService.kt` |

### 4. 统一 Sync 层注释

| 文件 | 路径 |
|------|------|
| WriteCalendar | `sync/WriteCalendar.kt` |
| Api2AttendanceQueryWorker | `sync/Api2AttendanceQueryWorker.kt` |
| Api2PollingService | `sync/Api2PollingService.kt` |
| BootReceiver | `sync/BootReceiver.kt` |

### 5. 完善 UI 层注释

| 文件 | 状态 |
|------|------|
| HomeScreen | ✅ 已完成 |
| CompetitionScreen | ✅ 已完成 |
| ToolsScreen | ✅ 已完成 |
| IntegrationScreen | ⏳ 待完成 |
| MainScreen | ⏳ 待完成 |
| LogDisplay | ⏳ 待完成 |
| ScheduleScreen | ⏳ 待完成 |
| MainViewModel | ⏳ 待完成 |

## 实施步骤

1. 分批次修改，每个模块完成后提交
2. 使用中文 KDoc 格式：`/** 描述 */`
3. 函数注释格式：`/** 功能描述 @param 参数说明 @return 返回值说明 */`

## 验证

- 运行 `./gradlew assembleDebug` 确保编译通过
- 运行 `./gradlew ktlintCheck` 检查代码风格

## 后续工作

- 运行 ktlintFormat 自动修复格式问题
- 添加单元测试
