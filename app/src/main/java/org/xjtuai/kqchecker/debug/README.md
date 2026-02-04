# Debug Package

此包包含所有与测试和调试相关的代码，便于快速查找和维护。

## 文件说明

### MockRepository.kt
- **用途**：从 assets 加载测试数据（weekly.json、periods.json）
- **使用场景**：本地开发测试，无需连接真实 API
- **关键方法**：
  - `loadWeeklyFromAssets()` - 加载周日程测试数据
  - `loadPeriodsFromAssets()` - 加载学期测试数据

### DebugRepository.kt
- **用途**：调试网络请求和认证
- **使用场景**：测试 TokenInterceptor 和 API 请求
- **关键方法**：
  - `performDebugRequest()` - 发起调试网络请求，验证 token 和 header

### DebugWorkers.kt
- **包含类**：
  - `TestWriteCalendar` - 测试日历写入功能
  - `SyncWorker` - 遗留的同步测试 Worker

### Api2QueryTestWorker.kt
- **用途**：测试 API2 查询流程
- **使用场景**：
  - 验证无考勤逻辑
  - 测试通知发送
  - 测试广播接收器
- **功能**：
  - 读取 assets/example_weekly_cleaned.json
  - 对每个课程发起 api2 查询
  - 保存查询结果到 api2_query_test_result.json
  - 强制模式（force=true）无视时间限制

## 使用方式

### 在 RepositoryProvider 中注册
```kotlin
fun getDebugRepository(): DebugRepository = debugRepository
fun getMockRepository(): MockRepository = mockRepository
```

### 在 MainActivity 中使用
```kotlin
// 调试请求
val result = RepositoryProvider.getDebugRepository().performDebugRequest()

// 手动测试 Api2QueryTestWorker
val req = OneTimeWorkRequestBuilder<Api2QueryTestWorker>()
    .setInputData(Data.Builder().putBoolean("force", true).build())
    .build()
WorkManager.getInstance(context).enqueue(req)
```

## 注意事项

1. **生产环境**：这些代码仅用于开发调试，**不应在生产应用中使用**
2. **示例文件**：确保 assets 目录中包含必要的示例数据文件
3. **权限**：某些调试功能需要相应的权限（如 WRITE_CALENDAR）
4. **性能**：Debug Worker 可能耗时较长，仅用于测试

## 后续改进

- 考虑使用 Gradle 构建变体 (`src/debug/`) 将这些代码完全分离到调试构建中
- 添加更多的测试工具类
- 增加单元测试覆盖
