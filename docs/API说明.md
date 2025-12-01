**API 说明（简体中文）**

- **位置**: `app/src/main/java/org/example/kqchecker/network/ApiService.kt`
- **HTTP 客户端**: OkHttp + Retrofit（`ApiClient` 创建客户端并注入 `TokenInterceptor`）
- **认证**: 请求头由 `TokenInterceptor` 添加
  - `synjones-auth`: `bearer <token>`（首选）
  - `Authorization`: `bearer <token>`（兼容性）
  - Token 来源：`app/src/main/java/org/example/kqchecker/auth/TokenManager.kt`

**基础 URL**
- 配置文件: `app/src/main/assets/config.json`
- 代码读取位置: `getBaseUrl()`（见各 Repository）
- 注意: `base_url` 应以 `/` 结尾，且不应包含与 `ApiService` 注解路径重复的片段。
  - 例如本仓库最终使用: `"http://bkkq.xjtu.edu.cn/"`

**主要端点（当前实现）**
- 获取周课表（API1）
  - 注解路径: `POST attendance-student/rankClass/getWeekSchedule2`
  - Retrofit 方法: `suspend fun getWeeklyData(@Body requestBody: RequestBody): ResponseBody?`
  - 调用方: `WeeklyRepository`（`app/src/main/java/.../repository/WeeklyRepository.kt`）
  - 请求示例 RequestBody: JSON
    {
      "action": "getWeekSchedule2",
      // 其它业务参数由调用方构建
    }
  - 返回: 服务端返回 JSON（示例 `{ "code":200, "success":true, "data": [...] }`），仓库通过 `ResponseBody.string()` 手动解析为 `JSONObject`。

- 获取水课表（API2）
  - 注解路径: `POST attendance-student/waterList/page`
  - Retrofit 方法: `suspend fun getWaterListData(@Body requestBody: RequestBody): ResponseBody?`
  - 调用方: `WaterListRepository`（`app/src/main/java/.../repository/WaterListRepository.kt`）以及 `Api2AttendanceQueryWorker` 在后台使用
  - 请求示例 RequestBody: JSON
    {
      "action": "getWaterList",
      "date": "2025-12-02",
      "termno": "<optional>"
    }
  - 返回: 服务端返回分页结构，当前项目用 `WaterListResponse.fromJson` 解析：
    - 支持 `code`、`success`、`data`（包含 `records`, `total`, `pages` 等字段）
    - 仓库当前接受 `success == true` 为成功（后端返回 `code==200` 时也可成功）

**关于 Retrofit 返回类型**
- 由于项目使用 `org.json` 手工解析，接口定义为 `ResponseBody?`，调用方负责调用 `respBody.string()` 并转换为 `JSONObject` 或自定义数据类（`WeeklyResponse`, `WaterListResponse`）。
- 原先将方法声明为 `JSONObject` 会触发运行时异常：`Unable to create converter for class org.json.JSONObject`，因此统一使用 `ResponseBody`。

**认证与拦截器**
- `TokenInterceptor` 会从 `TokenManager` 读取 token，并将 `synjones-auth` 与 `Authorization` 头加入请求。
- 当后台返回表明 token 失效时（Worker/Repository 检测到认证错误码），当前策略是记录日志并通过 Notification 提示用户重新登录（不在客户端尝试刷新 token）。

**缓存与文件**
- 缓存管理类: `CacheManager`（路径和文件名常量）
  - `WEEKLY_CACHE_FILE = "weekly.json"`
  - `WEEKLY_RAW_CACHE_FILE = "weekly_raw.json"`
  - `WEEKLY_RAW_META_FILE = "weekly_raw_meta.json"`（包含 `http_code`、`fetched_at`、`expires` 等元信息）
  - `WATER_LIST_CACHE_FILE = "api2_waterlist_response.json"`
- 仓库会在成功获取 API 响应后写入缓存文件（并在 raw meta 中写入 `expires`）

**后台任务（Worker）**
- `Api2AttendanceQueryWorker` 会读取 `weekly_cleaned.json`（由 `WeeklyCleaner` 生成，键为具体时间字符串 `YYYY-MM-DD HH:MM[:SS]`）并在事件前5分钟到后10分钟窗口内发起 API2 查询。
- Worker 使用 `WaterListRepository` 调用 API2，并将查询记录保存到 `api2_query_log.json`（仅当实际执行查询时写入）。

**常见错误与调试步骤**
1. Retrofit converter 错误
   - 错误示例: `Unable to create converter for class org.json.JSONObject`
   - 原因: 接口返回类型声明为 `JSONObject`，Retrofit 没有内置该 converter。
   - 解决: 将接口返回声明改为 `ResponseBody?`，并在调用处使用 `.string()` 手工解析。

2. 404 Not Found
   - 检查 `base_url`（`config.json`）是否和 `@POST` 注解路径重复或缺失片段。
   - 示例：若 `base_url` 包含 `attendance-student-pc` 而 `@POST` 也包含相关路径，会导致最终拼接错误。
   - 调试命令（adb logcat 过滤）：
     ```powershell
     adb logcat -d | Select-String -Pattern "WaterListRepository|Api2AttendanceQuery|getWaterList|attendance-student|HTTP 404" -AllMatches
     ```

3. API 返回格式与解析不匹配
   - 仓库当前对 API2 的成功判定是 `wr.success == true`；某些后端也使用 `code==0` 表示成功，请按后端实际返回调整解析条件。

**开发者快速示例**
- 构建并安装 APK:
  ```powershell
  ./gradlew assembleDebug
  adb install -r app\build\outputs\apk\debug\app-debug.apk
  ```
- 触发手动 API2 查询（在 App 内点击“Manual Api2 Query”），或用 adb 查看 Worker 日志：
  ```powershell
  adb logcat -d | Select-String -Pattern "Api2AttendanceQuery|WaterListRepository|api2 resp" -AllMatches
  ```
- 查看缓存内容（设备上）:
  ```powershell
  adb shell run-as org.example.kqchecker cat files/api2_waterlist_response.json
  adb shell run-as org.example.kqchecker cat files/weekly.json
  ```

**代码位置索引（快速查找）**
- `ApiService.kt` — Retrofit 接口和 `jsonToRequestBody`
- `ApiClient.kt` — OkHttp client 创建与 `TokenInterceptor` 注入
- `TokenInterceptor.kt` — 将 token 写入请求头
- `WeeklyRepository.kt` — API1 调用、缓存、cleaner 调用点
- `WaterListRepository.kt` — API2 调用、缓存、响应解析
- `Api2AttendanceQueryWorker.kt` — 后台扫描并发起 API2 查询的 Worker
- `WeeklyCleaner.kt` — 将原始 weekly 转成 `weekly_cleaned.json`（以时间为键）
