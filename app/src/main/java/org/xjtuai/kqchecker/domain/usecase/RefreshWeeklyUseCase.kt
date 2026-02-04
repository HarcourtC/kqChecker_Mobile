package org.xjtuai.kqchecker.domain.usecase

import android.util.Log
import org.xjtuai.kqchecker.auth.AuthRequiredException
import org.xjtuai.kqchecker.repository.CacheStatus
import org.xjtuai.kqchecker.repository.WeeklyRepository

/**
 * UseCase for refreshing weekly attendance data
 * Encapsulates business logic for fetching and validating weekly cache
 */
class RefreshWeeklyUseCase(private val weeklyRepository: WeeklyRepository) {

  suspend fun autoRefresh(): RefreshResult {
    return try {
      val cacheStatus = weeklyRepository.getCacheStatus()
      if (!cacheStatus.exists || cacheStatus.isExpired) {
        val result = weeklyRepository.refreshWeeklyData()
        if (result != null) {
          val updatedStatus = weeklyRepository.getCacheStatus()
          RefreshResult.Success(
            message = "Auto-refreshed and saved weekly.json",
            cacheStatus = updatedStatus
          )
        } else {
          RefreshResult.Error("Auto-refresh failed: Repository returned null")
        }
      } else {
        RefreshResult.CacheValid(
          message = "Weekly cache is up-to-date",
          expiresDate = cacheStatus.expiresDate ?: "Unknown"
        )
      }
    } catch (e: AuthRequiredException) {
      Log.w("RefreshWeeklyUseCase", "Auth required during auto-refresh", e)
      RefreshResult.AuthRequired(e.message ?: "Authentication required")
    } catch (e: Exception) {
      Log.e("RefreshWeeklyUseCase", "auto-refresh failed", e)
      RefreshResult.Error(e.message ?: e.toString())
    }
  }

  suspend fun manualRefresh(): RefreshResult {
    return try {
      val result = weeklyRepository.refreshWeeklyData()
      if (result != null) {
        val cacheStatus = weeklyRepository.getCacheStatus()
        RefreshResult.Success(
          message = "Sync completed successfully",
          cacheStatus = cacheStatus
        )
      } else {
        RefreshResult.Error("Sync failed - null result")
      }
    } catch (e: AuthRequiredException) {
      Log.w("RefreshWeeklyUseCase", "Auth required during manual refresh", e)
      RefreshResult.AuthRequired(e.message ?: "Authentication required")
    } catch (e: Exception) {
      Log.e("RefreshWeeklyUseCase", "manual refresh failed", e)
      RefreshResult.Error(e.message ?: e.toString())
    }
  }

  suspend fun checkCacheStatus(): CacheStatus {
    return weeklyRepository.getCacheStatus()
  }

  sealed class RefreshResult {
    data class Success(
      val message: String,
      val cacheStatus: CacheStatus
    ) : RefreshResult()

    data class CacheValid(
      val message: String,
      val expiresDate: String
    ) : RefreshResult()

    data class AuthRequired(val message: String) : RefreshResult()
    data class Error(val message: String) : RefreshResult()
  }
}
