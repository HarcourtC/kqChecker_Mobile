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
- **UI Framework**: Jetpack Compose 1.5.3 (Material 3)
- **Network**: Retrofit 2.9.0 + Moshi + OkHttp 4.11.0
- **Async**: Kotlin Coroutines 1.7.0 + Flow
- **Background Tasks**: WorkManager 2.8.1
- **Storage**: EncryptedSharedPreferences + Files (JSON caching)
- **SDK**: Target SDK 36, Min SDK 21
- **Architecture**: MVVM + Repository + Clean Architecture (Use Cases)

### Code Structure

```
app/src/main/java/org/xjtuai/kqchecker/
├── MainActivity.kt                    # Main entry point
├── KqApplication.kt                   # App initialization
│
├── auth/                              # Authentication module
│   ├── TokenStore.kt                  # Token persistence via EncryptedSharedPreferences
│   ├── TokenManager.kt                # Token operations (fetch, refresh)
│   ├── TokenInterceptor.kt            # Adds auth token to requests
│   ├── TokenAuthenticator.kt          # Retrofit token refresh on 401
│   └── WebLoginActivity.kt            # Web-based login UI
│
├── network/                           # Network/API layer
│   ├── NetworkModule.kt               # Singleton Retrofit & OkHttp setup
│   ├── ApiService.kt                  # Retrofit API interface definitions
│   ├── ApiClient.kt                   # OkHttp client configuration
│   ├── TokenResponse.kt               # API response models
│   ├── WeeklyResponse.kt              # Weekly attendance data model
│   ├── WaterListResponse.kt           # Water list (api2) data model
│   ├── CompetitionResponse.kt         # Competition data model
│   └── CompetitionApiInterceptor.kt    # Interceptor for Harco API
│
├── repository/                        # Data access & business logic layer
│   ├── RepositoryProvider.kt          # Singleton repository accessor
│   ├── WeeklyRepository.kt            # Fetches & caches weekly attendance (api1)
│   ├── WaterListRepository.kt         # Fetches & caches water list (api2)
│   ├── CompetitionRepository.kt       # Competition data with caching
│   ├── WeeklyCleaner.kt               # Data transformation/cleaning logic
│   ├── WeeklyRefreshWorker.kt         # WorkManager background sync
│   └── CacheManager.kt                # File-based caching (JSON files)
│
├── domain/usecase/                    # Clean Architecture use cases
│   ├── IntegrationFlowUseCase.kt      # End-to-end calendar sync workflow
│   ├── RefreshWeeklyUseCase.kt        # Weekly data refresh logic
│   ├── WriteCalendarUseCase.kt        # Calendar writing logic
│   └── Api2QueryUseCase.kt            # Water list query logic
│
├── ui/                                # UI layer (Jetpack Compose)
│   ├── MainScreen.kt                  # Main screen with navigation
│   ├── HomeScreen.kt                  # Home screen with sync controls
│   ├── CompetitionScreen.kt            # Competition info display
│   ├── IntegrationScreen.kt           # Calendar integration UI
│   ├── ToolsScreen.kt                 # Debug tools screen
│   ├── LogDisplay.kt                  # In-app log viewer
│   ├── viewmodel/
│   │   └── MainViewModel.kt           # Main UI state management
│   ├── state/
│   │   └── MainUiState.kt             # UI state models
│   ├── components/
│   │   └── CommonComponents.kt        # Reusable Compose components
│   └── theme/
│       ├── Theme.kt                   # Material 3 theme
│       └── Color.kt                   # Color definitions
│
├── sync/                              # Background sync
│   ├── Api2PollingService.kt          # Foreground service for api2 polling
│   ├── Api2AttendanceQueryWorker.kt   # WorkManager for attendance queries
│   ├── CompetitionDeadlineWorker.kt   # Competition deadline notifications
│   ├── WriteCalendar.kt               # Calendar writing service
│   └── BootReceiver.kt                # Restarts polling after device reboot
│
├── model/                             # Data models
│   ├── WeeklyModels.kt                # Weekly attendance models
│   └── PeriodModels.kt                # Academic period models
│
├── util/                              # Utilities
│   ├── CalendarHelper.kt              # Calendar integration helpers
│   ├── LoginHelper.kt                # Login flow helpers
│   ├── WorkManagerHelper.kt          # WorkManager configuration
│   ├── ConfigHelper.kt               # Configuration loading
│   └── NotificationHelper.kt         # Notification utilities
│
└── debug/                             # Debug/testing utilities
    ├── DebugRepository.kt             # Debug network requests
    ├── MockRepository.kt              # Load test data from assets
    ├── DebugWorkers.kt                # Test workers (calendar, sync)
    └── Api2QueryTestWorker.kt         # API2 query test worker
```

### Architecture Pattern: MVVM + Repository + Use Cases

**Flow**: UI (Compose Screens) → ViewModel → Use Cases → Repository → Network (ApiService) → Backend

1. **UI Layer** (Compose Screens): Material 3 UI, state management via ViewModel + StateFlow
2. **ViewModel**: Manages UI state, orchestrates use cases
3. **Use Cases** (domain/usecase): Business logic orchestration
   - `IntegrationFlowUseCase` - End-to-end calendar sync workflow
   - `RefreshWeeklyUseCase` - Weekly data refresh with caching
   - `WriteCalendarUseCase` - Calendar writing with conflict handling
4. **Repository Layer**: Abstracts data fetching, manages caching
   - `WeeklyRepository.fetchWeeklyData()` - Checks cache first, falls back to API
   - `WaterListRepository.fetchWaterListData()` - Water list with caching
   - `CompetitionRepository` - Competition data from Harco API
5. **Network Layer**: Retrofit services, interceptors, authentication

### Key Design Patterns

**Singleton Pattern**:
- `NetworkModule` - Single Retrofit/OkHttp instance
- `RepositoryProvider` - Single repository access point

**Repository Pattern**:
- Repositories hide network/cache complexity
- Always call repositories through use cases, never ApiService directly

**Interceptor Chain** (OkHttp):
1. TokenInterceptor (adds Authorization header)
2. CompetitionApiInterceptor (adds X-API-KEY for Harco API)
3. (If 401) TokenAuthenticator (refreshes token and retries)

**Async Patterns**:
- Suspend functions with `try/catch` for error handling
- Coroutines with `launch` or `async` for non-blocking operations
- WorkManager for background persistence across app restarts
- StateFlow for reactive UI updates

## Data Files & Caching

Cache files stored in `/data/data/org.xjtuai.kqchecker/files/`:
- **weekly.json** - Original API response (api1)
- **weekly_raw.json** - Backup of raw response
- **weekly_cleaned.json** - Processed data for calendar integration
- **api2_query_log.json** - Water list (api2) response for debugging
- **competition_data.json** - Competition data from Harco API
- **periods.json** - Academic period information

## Important Configuration

### Base URL
- **Default**: Defined in `app/build.gradle` `buildConfigField "BASE_URL"`
- **Public sample**: `assets/config.json`
- **Local private override**: create `assets/config.private.json`
- **Loaded in**: `KqApplication.onCreate()` via `ConfigHelper`

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
- `POST_NOTIFICATIONS` - Competition deadline notifications

## API Endpoints & Data Models

**Internal APIs** (require authentication):
- **API1** (Weekly Attendance): configured via `weekly_endpoint`
  - Repository: `WeeklyRepository.fetchWeeklyData()`
  - Response: `WeeklyResponse`

- **API2** (Water List / 考情流水): configured via `water_list_endpoint`
  - Repository: `WaterListRepository.fetchWaterListData()`
  - Response: `WaterListResponse`
  - Payload: `calendarBh` (term number), `startdate`, `enddate`, `pageSize`, `current`

**External APIs**:
- **Competition API**: configured via `competition_base_url` + `competition_endpoint`
  - Repository: `CompetitionRepository`
  - Auth: `X-API-KEY` header via `CompetitionApiInterceptor`, sourced from private config
  - Caches to: `competition_data.json`

## Development Workflow

### Running on Physical Device
1. Connect Android device via USB, enable USB debugging
2. Verify connection: `adb devices`
3. In Android Studio: Click Run → Select your device → OK
4. View logs: Android Studio Logcat, filter by tag "kqchecker" or app package

### Debugging Weekly Data Issues
```bash
# Clear cache files
adb shell run-as org.xjtuai.kqchecker rm -f /data/data/org.xjtuai.kqchecker/files/weekly*.json

# Pull and inspect cache files
adb shell run-as org.xjtuai.kqchecker cat /data/data/org.xjtuai.kqchecker/files/weekly.json > weekly.json

# View filtered logs
adb logcat -v time WeeklyRepository:* MainActivity:* WeeklyCleaner:* *:S
```

### Debugging Tools in App
MainActivity provides debug features accessible via ToolsScreen:
- **Fetch Weekly (API)** - Force fetch from API1, print raw response
- **Print cleaned weekly** - Display processed data in UI and Logcat
- **Print api2_query_log** - Show raw API2 response
- **Run Integration** - End-to-end calendar sync workflow
- **Debug Request** - Manual API request testing interface

### Mock Data for Development
- `assets/example_weekly.json` - Mock weekly data fallback
- `assets/example_weekly_cleaned.json` - Mock cleaned data
- `assets/periods.json` - Mock academic periods
- Use `MockRepository` to load from assets for offline testing

### Adding New Features
1. Add API endpoint to `ApiService.kt` (or create response model)
2. Create repository in `repository/` with caching logic
3. Create use case in `domain/usecase/` for business logic
4. Add UI in `ui/` (screen, update ViewModel)
5. Wire up in `MainViewModel` and navigation

## Common Issues & Solutions

**Crash on First Run**
- Check logcat for network errors (DNS, connection timeout)
- Verify BASE_URL is correct in `NetworkModule` or `assets/config.json`
- Ensure backend is reachable

**Token Expiration / 401 Errors**
- TokenAuthenticator should handle refresh automatically
- If it fails, user is prompted to re-login
- Tokens stored securely in EncryptedSharedPreferences

**Weekly Data Not Syncing**
- Check cache files exist: `adb shell run-as org.xjtuai.kqchecker ls /data/data/org.xjtuai.kqchecker/files/`
- Clear cache and force re-fetch via ToolsScreen
- Review logs for parsing errors in WeeklyCleaner

**WorkManager Background Tasks Not Running**
- Device may have aggressive battery optimization; whitelist app if needed
- Check WorkManager logs: `adb logcat | grep WorkManager`
- Verify workers are properly enqueued in code

## Additional Documentation

- **运行指南.md** - Detailed device setup & testing steps (Chinese)
- **模块拆分方案.md** - Architecture refactoring plan (Chinese)
- **API说明.md** - API endpoint specifications (Chinese)
- **COMPETITION_FEATURE_GUIDE.md** - Competition module details
- **CONTRIBUTING.md** - Contribution guidelines & code standards
- **README.md** - Project overview & installation
