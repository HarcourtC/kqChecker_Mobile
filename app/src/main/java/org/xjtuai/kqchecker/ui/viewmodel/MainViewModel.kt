package org.xjtuai.kqchecker.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.domain.usecase.Api2QueryUseCase
import org.xjtuai.kqchecker.domain.usecase.IntegrationFlowUseCase
import org.xjtuai.kqchecker.domain.usecase.RefreshWeeklyUseCase
import org.xjtuai.kqchecker.domain.usecase.WriteCalendarUseCase
import org.xjtuai.kqchecker.repository.CacheManager
import org.xjtuai.kqchecker.repository.RepositoryProvider
import org.xjtuai.kqchecker.repository.WeeklyCleaner
import org.xjtuai.kqchecker.repository.WeeklyRepository
import org.xjtuai.kqchecker.ui.state.MainUiState

/**
 * ViewModel for MainActivity
 * Centralizes state management and business logic orchestration
 * Eliminates ~350 lines from MainActivity through delegation
 */
class MainViewModel(
  application: Application,
  private val refreshWeeklyUseCase: RefreshWeeklyUseCase,
  private val writeCalendarUseCase: WriteCalendarUseCase,
  private val api2QueryUseCase: Api2QueryUseCase,
  private val integrationFlowUseCase: IntegrationFlowUseCase,
  private val weeklyRepository: WeeklyRepository,
  private val weeklyCleaner: WeeklyCleaner
) : AndroidViewModel(application) {

  private val context = application.applicationContext
  private val _uiState = MutableStateFlow(MainUiState())
  val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

  companion object {
    private const val TAG = "MainViewModel"
  }

  init {
    // Perform initialization on app start
    checkAndAutoRefresh()
    loadApi2Settings()
  }

  /**
   * Check cache expiration and auto-refresh if needed
   */
  private fun checkAndAutoRefresh() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        addEventLog("Checking weekly.json cache expiration...")
        val result = refreshWeeklyUseCase.autoRefresh()

        when (result) {
          is RefreshWeeklyUseCase.RefreshResult.Success -> {
            addEventLog(result.message)
            addEventLog("Cache will expire on: ${result.cacheStatus.expiresDate ?: "unknown"}")
          }
          is RefreshWeeklyUseCase.RefreshResult.CacheValid -> {
            addEventLog(result.message)
            addEventLog("Expires on: ${result.expiresDate}")
          }
          is RefreshWeeklyUseCase.RefreshResult.AuthRequired -> {
            addEventLog(result.message)
            _uiState.update { it.copy(needsLogin = true) }
          }
          is RefreshWeeklyUseCase.RefreshResult.Error -> {
            addEventLog("Auto-refresh check failed: ${result.message}")
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error during auto-refresh check", e)
        addEventLog("Auto-refresh check failed: ${e.message ?: e.toString()}")
      }
    }
  }

  /**
   * Load API2 settings from SharedPreferences
   */
  private fun loadApi2Settings() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val api2Auto = api2QueryUseCase.isPeriodicEnabled()
        val api2Foreground = api2QueryUseCase.isForegroundEnabled()

        _uiState.update { state ->
          state.copy(
            api2AutoEnabled = api2Auto,
            api2ForegroundEnabled = api2Foreground
          )
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error loading API2 settings", e)
      }
    }
  }

  /**
   * Add an event log entry to the UI
   */
  fun addEventLog(message: String) {
    viewModelScope.launch(Dispatchers.Main) {
      _uiState.update { state ->
        state.copy(eventLogs = state.eventLogs + message)
      }
    }
  }

  /**
   * Clear all event logs
   */
  fun clearEventLogs() {
    _uiState.update { it.copy(eventLogs = emptyList()) }
  }

  /**
   * Navigate to a specific page
   */
  fun onPageChange(newPage: Int) {
    _uiState.update { it.copy(currentPage = newPage) }
  }

  /**
   * Perform manual refresh of weekly data
   */
  fun onRefreshWeekly() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        addEventLog("Triggering manual sync...")
        val result = refreshWeeklyUseCase.manualRefresh()

        when (result) {
          is RefreshWeeklyUseCase.RefreshResult.Success -> {
            addEventLog(result.message)
            addEventLog("Cache status: ${if (result.cacheStatus.isExpired) "Expired" else "Valid"}")
            addEventLog("Cache expires on: ${result.cacheStatus.expiresDate ?: "unknown"}")
          }
          is RefreshWeeklyUseCase.RefreshResult.AuthRequired -> {
            addEventLog(result.message)
            _uiState.update { it.copy(needsLogin = true) }
          }
          is RefreshWeeklyUseCase.RefreshResult.Error -> {
            addEventLog("Sync exception: ${result.message}")
          }
          else -> addEventLog("Sync: unexpected result state")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Refresh weekly error", e)
        addEventLog("Sync exception: ${e.message}")
      }
    }
  }

  /**
   * Check cache status
   */
  fun onCheckCacheStatus() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val cacheStatus = refreshWeeklyUseCase.checkCacheStatus()
        addEventLog("[Home] Cache exists: ${cacheStatus.exists}, expired: ${cacheStatus.isExpired}")
      } catch (e: Exception) {
        addEventLog("[Home] Cannot read cache status: ${e.message ?: e.toString()}")
      }
    }
  }

  /**
   * Initiate calendar write operation
   */
  fun onWriteCalendar() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        addEventLog("Writing calendar from backend...")
        val result = writeCalendarUseCase.writeCalendarFromBackend()

        when (result) {
          is WriteCalendarUseCase.WriteResult.Enqueued -> {
            addEventLog(result.message)
            // Start observing the work
            writeCalendarUseCase.observeCalendarWriteStatus(result.workId) { status ->
              addEventLog(status)
            }
          }
          is WriteCalendarUseCase.WriteResult.Error -> {
            addEventLog("Calendar write error: ${result.message}")
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Write calendar error", e)
        addEventLog("Calendar write error: ${e.message ?: e.toString()}")
      }
    }
  }

  /**
   * Execute experimental sync (API2)
   */
  fun onExperimentalSync() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        addEventLog("Running experimental sync (API2)...")
        val waterListRepo = RepositoryProvider.getWaterListRepository()
        val result = waterListRepo.refreshWaterListData()

        if (result != null) {
          addEventLog("Experimental sync completed successfully")
          addEventLog("API2 data fetched and saved")
        } else {
          addEventLog("Experimental sync failed - null result")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Experimental sync error", e)
        addEventLog("Experimental sync exception: ${e.message ?: e.toString()}")
      }
    }
  }

  /**
   * Enqueue a manual API2 query
   */
  fun onManualApi2Query() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        addEventLog("Manual api2 query triggered")
        val result = api2QueryUseCase.enqueueManualQuery()

        when (result) {
          is Api2QueryUseCase.QueryResult.Success -> addEventLog(result.message)
          is Api2QueryUseCase.QueryResult.Error -> addEventLog("Failed to enqueue: ${result.message}")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Manual API2 query error", e)
        addEventLog("Failed to enqueue: ${e.message}")
      }
    }
  }

  /**
   * Toggle periodic API2 queries
   */
  fun onToggleApi2Periodic(enabled: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val result = if (enabled) {
          api2QueryUseCase.enablePeriodicQueries()
        } else {
          api2QueryUseCase.disablePeriodicQueries()
        }

        when (result) {
          is Api2QueryUseCase.QueryResult.Success -> {
            addEventLog(result.message)
            _uiState.update { it.copy(api2AutoEnabled = enabled) }
          }
          is Api2QueryUseCase.QueryResult.Error -> {
            addEventLog("Failed to toggle periodic queries: ${result.message}")
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Toggle periodic queries error", e)
        addEventLog("Failed: ${e.message}")
      }
    }
  }

  /**
   * Toggle foreground polling
   */
  fun onToggleForegroundPolling(enabled: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val result = if (enabled) {
          api2QueryUseCase.startForegroundPolling(5)
        } else {
          api2QueryUseCase.stopForegroundPolling()
        }

        when (result) {
          is Api2QueryUseCase.QueryResult.Success -> {
            addEventLog(result.message)
            _uiState.update { it.copy(api2ForegroundEnabled = enabled) }
          }
          is Api2QueryUseCase.QueryResult.Error -> {
            addEventLog("Failed to toggle foreground polling: ${result.message}")
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Toggle foreground polling error", e)
        addEventLog("Failed: ${e.message}")
      }
    }
  }

  /**
   * Execute integration flow (ensure cleaned weekly and write to calendar)
   */
  fun onStartIntegration() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        addEventLog("[Integration] Starting integration flow...")

        // Step 1: Execute integration flow
        val flowResult = integrationFlowUseCase.executeIntegrationFlow()
        when (flowResult) {
          is IntegrationFlowUseCase.IntegrationResult.CleanedWeeklyExists -> {
            addEventLog(flowResult.message)
          }
          is IntegrationFlowUseCase.IntegrationResult.DataExists -> {
            addEventLog(flowResult.message)
            // Generate cleaned weekly
            val cleanResult = integrationFlowUseCase.generateCleanedWeekly()
            when (cleanResult) {
              is IntegrationFlowUseCase.GenerateCleanedResult.Success -> {
                addEventLog(cleanResult.message)
              }
              is IntegrationFlowUseCase.GenerateCleanedResult.Error -> {
                addEventLog("[Integration] Generation failed: ${cleanResult.message}")
                return@launch
              }
            }
          }
          is IntegrationFlowUseCase.IntegrationResult.DataFetched -> {
            addEventLog(flowResult.message)
            // Generate cleaned weekly
            val cleanResult = integrationFlowUseCase.generateCleanedWeekly()
            when (cleanResult) {
              is IntegrationFlowUseCase.GenerateCleanedResult.Success -> {
                addEventLog(cleanResult.message)
              }
              is IntegrationFlowUseCase.GenerateCleanedResult.Error -> {
                addEventLog("[Integration] Generation failed: ${cleanResult.message}")
                return@launch
              }
            }
          }
          is IntegrationFlowUseCase.IntegrationResult.AuthRequired -> {
            addEventLog(flowResult.message)
            _uiState.update { it.copy(needsLogin = true) }
            return@launch
          }
          is IntegrationFlowUseCase.IntegrationResult.Error -> {
            addEventLog("[Integration] Error: ${flowResult.message}")
            return@launch
          }
        }

        // Step 2: Submit calendar write task
        integrationFlowUseCase.submitAndMonitorCalendarWrite { status ->
          addEventLog(status)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Integration flow error", e)
        addEventLog("[Integration] Unexpected error: ${e.message}")
      }
    }
  }

  /**
   * Handle integration pending state for permission callback
   */
  fun setIntegrationPending(pending: Boolean) {
    _uiState.update { it.copy(integrationPending = pending) }
  }

  /**
   * Generate cleaned weekly manually
   */
  fun onGenerateCleanedWeekly() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        addEventLog("Generating cleaned weekly...")
        val result = integrationFlowUseCase.generateCleanedWeekly()

        when (result) {
          is IntegrationFlowUseCase.GenerateCleanedResult.Success -> {
            addEventLog(result.message)
            val cleanedPath = weeklyCleaner.getCleanedFilePath()
            if (cleanedPath != null) {
              addEventLog("Saved cleaned weekly: $cleanedPath")
            }
          }
          is IntegrationFlowUseCase.GenerateCleanedResult.Error -> {
            addEventLog("Exception during cleaning: ${result.message}")
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Generate cleaned weekly error", e)
        addEventLog("Exception during cleaning: ${e.message ?: e.toString()}")
      }
    }
  }

  /**
   * Print cleaned weekly file contents
   */
  fun onPrintCleanedWeekly() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        addEventLog("Printing cleaned weekly to logs...")
        val cm = CacheManager(context)
        val content = cm.readFromCache("cleaned_weekly.json")

        if (content.isNullOrBlank()) {
          addEventLog("No cleaned weekly found in cache")
        } else {
          Log.d(TAG, "Cleaned weekly (${content.length} bytes):\n$content")
          addEventLog("Printed cleaned weekly to logs (${content.length} bytes)")
          addEventLog("Preview: ${content.take(800)}")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Print cleaned weekly error", e)
        addEventLog("Print cleaned weekly failed: ${e.message ?: e.toString()}")
      }
    }
  }

  /**
   * Print weekly files
   */
  fun onPrintWeekly() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        addEventLog("Printing weekly files...")
        val previews = weeklyRepository.getWeeklyFilePreviews()

        if (previews.isEmpty()) {
          addEventLog("No weekly files found to print")
          return@launch
        }

        for (p in previews) {
          Log.d(TAG, "File: ${p.name} (${p.size} bytes)\n${p.preview}")
          addEventLog("Printed ${p.name} (${p.size} bytes)")
          addEventLog("Preview: ${p.preview.take(200)}...")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Print weekly error", e)
        addEventLog("Print failed: ${e.message ?: e.toString()}")
      }
    }
  }
}
