package org.xjtuai.kqchecker.ui.state

import org.xjtuai.kqchecker.model.ScheduleItem

/**
 * Represents the complete UI state for MainActivity
 * Centralizes all state management for easier testing and composition
 */
data class MainUiState(
  // Navigation state
  val currentPage: Int = 0, // 0 = Home, 1 = Tools, 2 = Integration

  // Schedule Data
  val scheduleItems: List<ScheduleItem> = emptyList(),

  // API2 settings
  val api2AutoEnabled: Boolean = false,
  val api2ForegroundEnabled: Boolean = false,

  // Integration flow state
  val integrationPending: Boolean = false,

  // Event logs for UI display
  val eventLogs: List<String> = emptyList(),

  // Loading/UI state flags
  val isLoading: Boolean = false,
  val needsLogin: Boolean = false
)
