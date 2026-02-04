# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Build & Run Commands

### Build
```bash
# Debug build (Windows)
gradlew assembleDebug

# Debug build (Linux/Mac)
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

### Run on Device
```bash
# After device is connected via USB with debugging enabled
gradlew installDebug

# Or manually install after build
adb install -r app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat | grep -i "kqchecker"
```

### Android Studio
- Run: Click green Run button or press **Shift+F10**
- Debug: Click green Debug button or press **Shift+F9**
- Rebuild: **Build → Rebuild Project** or press **Ctrl+F9**

## Project Architecture

### Technology Stack
- **Language**: Kotlin 1.9.10
- **UI Framework**: Jetpack Compose 1.5.3
- **Network**: Retrofit 2.9.0 + Moshi + OkHttp 4.11.0
- **Async**: Kotlin Coroutines 1.7.0
- **Background Tasks**: WorkManager 2.8.1
- **Storage**: EncryptedSharedPreferences + Files (JSON caching)
- **SDK**: Target SDK 36, Min SDK 21

### Code Structure

```
app/src/main/java/org/example/kqchecker/
├── MainActivity.kt                # Main UI (Jetpack Compose, ~800 lines)
├── KqApplication.kt               # App initialization, NetworkModule setup
│
├── auth/                          # Authentication module
│   ├── TokenStore.kt              # Token persistence via SharedPreferences
│   ├── TokenManager.kt            # Token operations (fetch, refresh)
│   ├── TokenAuthenticator.kt      # Retrofit token refresh interceptor
│   └── WebLoginActivity.kt        # Web-based login UI
│
├── network/                       # Network/API layer
│   ├── NetworkModule.kt           # Singleton Retrofit & OkHttp setup
│   ├── ApiService.kt              # Retrofit API interface definitions
│   ├── ApiClient.kt               # OkHttp client configuration
│   ├── TokenInterceptor.kt        # Adds auth token to requests
│   ├── TokenResponse.kt           # API response models
│   ├── WeeklyResponse.kt          # Weekly attendance data model
│   └── WaterListResponse.kt       # Water list (api2) data model
│
├── repository/                    # Data access & business logic layer
│   ├── RepositoryProvider.kt      # Singleton repository accessor
│   ├── WeeklyRepository.kt        # Fetches & caches weekly attendance (api1)
│   ├── WaterListRepository.kt     # Fetches & caches water list (api2)
│   ├── WeeklyCleaner.kt           # Data transformation/cleaning logic
│   ├── WeeklyRefreshWorker.kt     # WorkManager background sync
│   ├── CacheManager.kt            # File-based caching (JSON files)
│   └── DebugRepository.kt         # Debug/testing helpers
│
├── sync/                          # Background sync
│   ├── Api2PollingService.kt      # Foreground service for api2 polling
│   └── BootReceiver.kt            # Restarts polling after device reboot
│
├── model/                         # Data classes (if any)
├── util/                          # Utilities
│   ├── CalendarHelper.kt          # Calendar integration
│   └── Others...
└── repo/                          # Legacy code directory
```

### Architecture Pattern: MVVM + Repository

**Flow**: UI (MainActivity) → Repository → Network (ApiService) → Backend

1. **UI Layer** (MainActivity): Jetpack Compose UI, state management via Compose state holders
2. **Repository Layer**: Abstracts data fetching logic, manages caching, handles retries
   - `WeeklyRepository.fetchWeeklyData()` - Checks cache first, falls back to API
   - `WaterListRepository.fetchWaterListData()` - Same pattern for water list
3. **Network Layer**: Retrofit services, interceptors, authentication
   - `ApiService` defines endpoints
   - `TokenInterceptor` adds auth header
   - `TokenAuthenticator` refreshes expired tokens
4. **Data Persistence**:
   - Tokens: EncryptedSharedPreferences (TokenStore)
   - JSON data: Local files via CacheManager

### Key Design Patterns

**Singleton Pattern**:
- `NetworkModule` - Single Retrofit/OkHttp instance
- `RepositoryProvider` - Single repository access point

**Repository Pattern**:
- Repositories hide network/cache complexity
- Always call repositories from UI, never ApiService directly

**Interceptor Chain** (OkHttp):
1. TokenInterceptor (adds Authorization header)
2. (If 401) TokenAuthenticator (refreshes token and retries)

**Async Patterns**:
- Suspend functions with `try/catch` for error handling
- Coroutines with `launch` or `async` for non-blocking operations
- WorkManager for background persistence across app restarts

## Data Files & Caching

Weekly attendance data caches to three files:
- **weekly.json** - Original API response (api1)
- **weekly_raw.json** - Backup of raw response
- **weekly_cleaned.json** - Processed/cleaned data for calendar integration
- **api2_query_log.json** - Water list (api2) response for debugging

Cache location: `/data/data/org.example.kqchecker/files/` (device storage)

## Important Configuration

### Base URL
- **Default**: Defined in `app/build.gradle` `buildConfigField "BASE_URL"`
- **Override**: Create `assets/config.json` with `{"base_url": "..."}`
- **Loaded in**: `KqApplication.onCreate()`

### Token Lifecycle
1. User logs in via WebLoginActivity
2. Token stored encrypted in SharedPreferences (TokenStore)
3. TokenInterceptor adds token to all API requests
4. If token expires (401), TokenAuthenticator refreshes it
5. On auth failure (403, 400, etc.), token is cleared, user prompted to re-login

### Permissions (AndroidManifest.xml)
- `INTERNET` - Network access
- `WRITE_CALENDAR` / `READ_CALENDAR` - Calendar integration
- `WAKE_LOCK` - Background task execution
- `RECEIVE_BOOT_COMPLETED` - Restart after device reboot

## API Endpoints & Data Models

**API1 (Weekly Attendance)**
- Endpoint: `/api1` (path varies based on BASE_URL)
- Response Model: `WeeklyResponse`
- Repository: `WeeklyRepository.fetchWeeklyData()`

**API2 (Water List / 考情流水)**
- Endpoint: `/api2` (path varies)
- Response Model: `WaterListResponse`
- Repository: `WaterListRepository.fetchWaterListData()`
- Payload: `calendarBh` (term number), `startdate`, `enddate`, `pageSize`, `current`

## Development Workflow

### Running on Physical Device
1. Connect Android device via USB, enable USB debugging
2. Verify connection: `adb devices`
3. In Android Studio: Click Run → Select your device → OK
4. View logs: Android Studio Logcat, filter by tag "kqchecker" or app package

### Debugging Weekly Data Issues
```bash
# Clear cache files
adb shell run-as org.example.kqchecker rm -f /data/data/org.example.kqchecker/files/weekly*.json

# Pull and inspect cache files
adb shell run-as org.example.kqchecker cat /data/data/org.example.kqchecker/files/weekly.json > weekly.json

# View filtered logs
adb logcat -v time WeeklyRepository:* MainActivity:* WeeklyCleaner:* *:S
```

### Adding New Network Endpoints
1. Add method to `ApiService` interface
2. Create response model class (e.g., `MyResponse.kt`)
3. Create repository class (e.g., `MyRepository.kt`) with caching logic
4. Call from MainActivity via `RepositoryProvider.getRepository().myMethod()`

### Modifying Cache Logic
- CacheManager handles file I/O (`readFile()`, `writeFile()`)
- WeeklyCleaner handles data transformation
- Override caching in individual repositories (`WeeklyRepository`, etc.)

## Common Issues & Solutions

**Crash on First Run**
- Check logcat for network errors (DNS, connection timeout)
- Verify BASE_URL is correct in `NetworkModule` or `assets/config.json`
- Ensure backend is reachable

**Token Expiration / 401 Errors**
- TokenAuthenticator should handle refresh automatically
- If it fails, user is prompted to re-login
- Tokens are stored securely in EncryptedSharedPreferences

**Weekly Data Not Syncing**
- Check cache files exist: `adb shell run-as org.example.kqchecker ls /data/data/org.example.kqchecker/files/`
- Clear cache and force re-fetch: Use debug buttons in MainActivity
- Review logs for parsing errors in WeeklyCleaner

**WorkManager Background Tasks Not Running**
- Device may have aggressive battery optimization; whitelist app if needed
- Check WorkManager logs: `adb logcat | grep WorkManager`
- Verify `WeeklyRefreshWorker` is properly enqueued in code

## Contribution Guidelines (from CONTRIBUTING.md)

### Kotlin Code Style
- 2-space indentation
- Class names: PascalCase
- Function/variable names: camelCase
- Constants: ALL_CAPS_WITH_UNDERSCORES
- Max line length: 100 characters
- Use suspend functions for async operations

### Compose Component Style
- Component names: PascalCase
- Avoid long-running ops in composables
- Use `remember`, `derivedStateOf` for memoization

### Branch & Commit Strategy
- **main**: Stable releases
- **develop**: Latest dev version
- Create feature branches: `feature/short-description`
- Commit format: `<type>: <description>` (e.g., `feat: add api2 support`)

### Testing
- Build after changes: `./gradlew assembleDebug`
- Install and manually test on device
- Check logs for errors/crashes
- For larger changes, test on API 21+ devices/emulators

## Debugging Tools in App

MainActivity provides debug buttons (visible in dev builds):
- **Fetch Weekly (API)** - Force fetch from API1, print raw response
- **Print cleaned weekly** - Display processed data in UI and Logcat
- **Print api2_query_log** - Show raw API2 response
- **Run Integration** - End-to-end calendar sync workflow
- **Debug Request** - Manual API request testing interface

Access via UI debug section at bottom of MainActivity Compose layout.

## Additional Documentation

- **运行指南.md** - Detailed device setup & testing steps (Chinese)
- **模块拆分方案.md** - Architecture refactoring plan (Chinese)
- **API说明.md** - API endpoint specifications (Chinese)
- **README.md** - Project overview & installation
- **CONTRIBUTING.md** - Contribution guidelines & code standards
