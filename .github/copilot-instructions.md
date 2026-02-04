# Origin_Mobile Codebase Instructions

You are an expert AI assistant working on the "Origin_Mobile" (kqChecker) Android project. This document outlines the project's architecture, conventions, and workflows to ensure consistent and high-quality code generation.

## 1. Project Overview
- **Type**: Android Mobile Application
- **Language**: Kotlin 1.9.10
- **UI Framework**: Jetpack Compose 1.5.3 (Material 3)
- **Architecture**: MVVM (Model-View-ViewModel) + Repository Pattern
- **Network**: Retrofit 2.9.0 + OkHttp 4.11.0 + Moshi
- **Async**: Kotlin Coroutines 1.7.0 + Flow
- **Background**: WorkManager 2.8.1

## 2. Key Directories & Structure
- `app/src/main/java/org/example/kqchecker/`: Root package
  - `ui/`: Compose UI components and Screens (Activity is thin).
  - `viewmodel/`: ViewModels managing UI state.
  - `repository/`: Data layer, managing API calls and caching.
  - `network/`: Retrofit services, interceptors, and DTOs.
  - `auth/`: Token management and login logic.
  - `model/`: Domain data models.
  - `util/`: Helper classes (Date, JSON, etc.).
  - `sync/`: Background workers and services.

## 3. Architecture & Conventions

### UI Layer (Jetpack Compose)
- **State Management**: Use `ViewModel` with `StateFlow` or `Compose State` (`mutableStateOf`).
- **Components**: Prefer small, reusable Composable functions.
- **Side Effects**: Use `LaunchedEffect` or `DisposableEffect` for side effects.
- **Threading**: UI code runs on `Main` thread. Long operations MUST be offloaded to `IO` dispatcher via Coroutines.

### Data Layer (Repository Pattern)
- **Single Source of Truth**: Repositories arbitrate between Remote (API) and Local (Cache) data.
- **Caching**: 
  - JSON files are used for caching (managed by `CacheManager`).
  - Key files: `weekly_raw.json` (API1), `api2_waterlist_response.json` (API2), `competition_data.json` (Harco API).
- **Error Handling**: Catch exceptions in Repository and return `Result` or nullable types to ViewModel.

### Network API
- **Client**: `OkHttpClient` with `TokenInterceptor` for authenticated requests.
- **Endpoints**:
  - **API1**: Weekly Schedule (`/attendance-student/rankClass/getWeekSchedule2`).
  - **API2**: Water List (`/attendance-student/waterList/page`).
  - **Competition**: Harco API (`https://api.harco.top/xjtudean`).
- **Authorization**:
  - Internal APIs use `synjones-auth` and `Authorization` headers (Bearer Token).
  - Harco API uses `X-API-KEY`.
- **Response Handling**: Raw `ResponseBody` is often used to avoid JSON parsing issues with some endpoints, then parsed manually using `org.json` or `Moshi`.

## 4. Feature Implementation Guidelines

### Adding a New API Endpoint
1.  Define the endpoint in `ApiService.kt`.
2.  Create a Response model in `network/` or use `ResponseBody` if dynamic.
3.  Add a method in the corresponding `Repository` (or create a new one).
4.  Implement caching logic in the Repository if needed.
5.  Expose data via a suspend function to the ViewModel.

### Working with Authentication
- Access tokens are stored in `TokenStore`.
- `TokenManager` handles retrieval and refreshing.
- `TokenInterceptor` automatically attaches tokens to requests.
- **DO NOT** manually add headers in UI code; let the interceptor handle it.

### Background Tasks
- Use `WorkManager` for periodic syncs (e.g., `WeeklyRefreshWorker`).
- Ensure logic is idempotent and handles network failures gracefully.

## 5. Coding Standards
- **Kotlin Style**: Follow official Kotlin coding conventions.
- **Coroutines**: Use `suspend` functions for I/O operations. Avoid `GlobalScope`.
- **Logging**: Use standard `Log.d/e` with consistent tags. The UI also has an event log feature; consider logging important user-facing events there.
- **Comments**: Document complex logic, especially for manual JSON parsing or data cleaning heuristics (`WeeklyCleaner`).

## 6. Debugging & Tools
- **Logs**: The app writes logs to `Logcat` and an in-app console.
- **Raw Data**: Use "Print weekly.json" or "Fetch Weekly (API)" in the app to inspect raw server responses.
- **Mock Data**: `assets/example_weekly.json` acts as a fallback/mock when the network is unavailable.

## 7. Documentation
- Refer to `docs/API说明.md` for detailed API specs.
- Refer to `docs/COMPETITION_FEATURE_GUIDE.md` for the competition module.
- `CLAUDE.md` contains additional developer notes.
