# 竞赛数据功能使用指南

## 功能概述
已成功添加竞赛数据获取功能，支持从 `https://api.harco.top/xjtudean` API 请求竞赛数据，并自动缓存到本地。

## 核心实现

### 新增文件
1. **CompetitionResponse.kt** - 数据模型
   - `CompetitionResponse` - 顶层响应
   - `CompetitionMeta` - 元数据（updateTime, total, method）
   - `CompetitionItem` - 竞赛项目（id, type, category, title, url, date, isNew）

2. **CompetitionApiInterceptor.kt** - API 请求拦截器
   - 自动添加 `X-API-KEY: HarcoSecret2026XJTUDeanApi` 请求头

3. **CompetitionRepository.kt** - 数据仓库
   - 处理数据获取、缓存和业务逻辑
   - 使用独立的 OkHttpClient（不同的 base URL 和拦截器）

### 修改的文件
1. **ApiService.kt** - 新增 GET 端点
2. **CacheManager.kt** - 新增缓存文件常量 `COMPETITION_CACHE_FILE = "competition_data.json"`
3. **RepositoryProvider.kt** - 注册 CompetitionRepository 单例

## 使用方法

### 在 Kotlin 代码中调用

```kotlin
// 获取竞赛数据仓库
val competitionRepo = RepositoryProvider.getCompetitionRepository()

// 从缓存或 API 获取竞赛数据（非强制刷新）
val competition = competitionRepo.getCompetitionData()

// 强制从 API 刷新数据（跳过缓存）
val freshData = competitionRepo.getCompetitionData(forceRefresh = true)

// 处理响应
if (competition != null) {
    val status = competition.status  // "success"
    val total = competition.meta.total  // 总数
    val items = competition.data  // 竞赛列表

    items.forEach { item ->
        Log.d("Competition", "标题: ${item.title}")
        Log.d("Competition", "日期: ${item.date}")
        Log.d("Competition", "链接: ${item.url}")
    }
}
```

### 在 Compose UI 中集成

```kotlin
@Composable
fun CompetitionScreen() {
    var competition by remember { mutableStateOf<CompetitionResponse?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            competition = RepositoryProvider.getCompetitionRepository()
                .getCompetitionData(forceRefresh = false)
            isLoading = false
        }
    }

    if (isLoading) {
        CircularProgressIndicator()
    } else if (competition != null) {
        LazyColumn {
            items(competition!!.data) { item ->
                CompetitionCard(item)
            }
        }
    } else {
        Text("无法加载竞赛数据")
    }
}

@Composable
fun CompetitionCard(item: CompetitionItem) {
    Card(modifier = Modifier.padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.title, fontWeight = FontWeight.Bold)
            Text(text = "分类: ${item.category}")
            Text(text = "日期: ${item.date}")
            if (item.isNew) {
                Text(text = "新", color = Color.Red)
            }
        }
    }
}
```

## 数据流

```
UI 层
  ↓
RepositoryProvider.getCompetitionRepository()
  ↓
CompetitionRepository.getCompetitionData()
  ├→ 检查缓存 (competition_data.json)
  │  ├→ 缓存存在 → 返回缓存数据
  │  └→ 缓存不存在 → 调用 API
  │
  └→ API 请求
     ├→ OkHttpClient (CompetitionApiInterceptor 添加 X-API-KEY)
     ├→ Retrofit GET /xjtudean
     ├→ 响应解析为 CompetitionResponse
     └→ 缓存响应到本地文件
```

## 缓存管理

### 缓存文件位置
`/data/data/org.example.kqchecker/files/competition_data.json`

### 查看缓存
```bash
# 连接到设备
adb shell run-as org.xjtuai.kqchecker cat /data/data/org.xjtuai.kqchecker/files/competition_data.json
```

### 清除缓存
```bash
# 删除缓存文件
adb shell run-as org.xjtuai.kqchecker rm /data/data/org.xjtuai.kqchecker/files/competition_data.json
```

## API 响应示例

```json
{
  "status": "success",
  "meta": {
    "updateTime": "2026-02-04T05:46:44.151Z",
    "total": 27,
    "method": "WAF Reverse Engineering"
  },
  "data": [
    {
      "id": "9731",
      "type": "jsap",
      "category": "竞赛安排",
      "title": "[竞赛安排]关于组织参加第九届中国高校智能机器人创意大赛的通知",
      "url": "https://jwc.xjtu.edu.cn/info/1172/9731.htm",
      "date": "2026-01-29",
      "isNew": false
    }
  ]
}
```

## 日志输出

应用会在 Logcat 中输出详细的竞赛数据获取日志，可通过以下方式查看：

```bash
# 查看竞赛仓库的日志
adb logcat | grep CompetitionRepository

# 查看竞赛 API 拦截器的日志
adb logcat | grep CompetitionApiInterceptor
```

## 架构设计要点

### 为什么使用独立的 OkHttpClient？
竞赛 API 与现有 API（周课表、水课表）有以下差异：
1. **基础 URL 不同**：`https://api.harco.top/` vs `http://bkkq.xjtu.edu.cn/attendance-student-pc`
2. **认证方式不同**：使用 `X-API-KEY` 而非 `synjones-auth` token
3. **请求方法不同**：GET 请求而非 POST
4. **不需要 Token 刷新**：没有 TokenAuthenticator

因此在 CompetitionRepository 中创建了专用的 Retrofit 实例。

### 缓存策略
- 首次加载：从 API 获取，保存到本地
- 后续加载：优先从缓存读取，无需网络请求
- 强制刷新：跳过缓存，直接从 API 获取
- 缓存更新：每次 API 请求成功后自动更新

## 构建和运行

```bash
# 构建
./gradlew assembleDebug

# 安装
./gradlew installDebug

# 查看完整日志
adb logcat -v time | grep -i competition
```

## 注意事项

1. **API Key 的安全性**：当前 API Key 是硬编码在 `CompetitionApiInterceptor` 中。如果需要更高的安全性，可考虑：
   - 使用加密存储
   - 从服务器端获取 API Key
   - 定期轮换 API Key

2. **网络超时**：默认超时为 30 秒（连接和读取）

3. **错误处理**：API 失败时返回 `null`，请在使用时检查空值

4. **缓存大小**：竞赛数据通常较小，不需要特殊的缓存管理

## 后续改进建议

1. 实现缓存过期时间（如 WeeklyRepository）
2. 添加重试机制处理网络波动
3. 实现增量更新（只获取新项目）
4. 添加更详细的错误提示
5. 支持离线模式（在无网络时返回缓存）
