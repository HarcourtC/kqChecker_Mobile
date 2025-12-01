# Origin_Mobile

这是为 `kqChecker` 项目生成的 Android Kotlin（Jetpack Compose）骨架，放在 `Origin_Mobile` 目录下。

主要功能（样板）:

- Compose `MainActivity` 示例界面
- Retrofit + Moshi 基本网络模块（`NetworkModule`）
- 简易 `TokenStore`（示例）
- `SyncWorker`（WorkManager CoroutineWorker 示例）

如何使用

1. 在 Android Studio 中打开该目录（选择 `Origin_Mobile` 作为项目根）。
2. 等待 Gradle 同步（Android Studio 会提示安装 Gradle wrapper 或插件）。
3. 在 `app` 模块中替换 `NetworkModule.BASE_URL` 为你的后端地址。
4. 实现登录并把 token 写入 `TokenStore`，在 `SyncWorker` 中补充处理逻辑。
5. 以模拟方式先运行 `MainActivity`，检查 UI 能否启动。

下一步建议

- 我可以把 Retrofit 接口映射（基于你现有 `real_api_response_*.json`）加入到项目中，并实现一个 mock repository 用于本地测试。
- 如果确认后端鉴权方式（JWT + refresh token），我会实现 `TokenInterceptor` 与刷新逻辑，并把 `EncryptedSharedPreferences` 加入项目。
