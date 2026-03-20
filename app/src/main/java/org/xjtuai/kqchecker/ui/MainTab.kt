package org.xjtuai.kqchecker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.xjtuai.kqchecker.model.ScheduleItem
import org.xjtuai.kqchecker.network.WaterRecord

sealed class MainTab(val route: String, val label: String, val icon: ImageVector) {
    object Home : MainTab("home", "首页", Icons.Default.Home)
    object Schedule : MainTab("schedule", "课表", Icons.Default.DateRange)
    object Competition : MainTab("competition", "竞赛", Icons.Default.List)
    object Tools : MainTab("tools", "工具", Icons.Default.Build)
    object Settings : MainTab("settings", "设置", Icons.Default.Settings)
}

@Composable
fun MainScreen(
    events: List<String>,
    scheduleItems: List<ScheduleItem>,
    latestAttendance: WaterRecord?,
    latestAttendanceHint: String?,
    homeRefreshToken: Long,
    isLoggedIn: Boolean,
    showEventLog: Boolean,
    isCheckingUpdate: Boolean,
    onPostEvent: (String) -> Unit,
    onLoginClick: () -> Unit,
    onManualSync: () -> Unit,
    onCheckUpdate: () -> Unit,
    onLoginRequired: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<MainTab>(MainTab.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        backgroundColor = MaterialTheme.colors.background,
        bottomBar = {
            BottomNavigation(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.primary,
                elevation = 10.dp
            ) {
                val screens = listOf(
                    MainTab.Home,
                    MainTab.Schedule,
                    MainTab.Competition,
                    MainTab.Tools,
                    MainTab.Settings
                )
                screens.forEach { screen ->
                    BottomNavigationItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentScreen) {
                    MainTab.Home -> HomeScreen(
                        onLoginClick = onLoginClick,
                        onManualSync = onManualSync,
                        isLoggedIn = isLoggedIn,
                        scheduleItems = scheduleItems,
                        latestAttendance = latestAttendance,
                        latestAttendanceHint = latestAttendanceHint,
                        refreshToken = homeRefreshToken
                    )
                    MainTab.Schedule -> ScheduleScreen(
                        onPostEvent = onPostEvent
                    )
                    MainTab.Competition -> CompetitionScreen(
                        onPostEvent = onPostEvent
                    )
                    MainTab.Tools -> ToolsScreen(
                        onPostEvent = onPostEvent,
                        onLoginRequired = onLoginRequired
                    )
                    MainTab.Settings -> SettingsScreen(
                        onPostEvent = onPostEvent,
                        isCheckingUpdate = isCheckingUpdate,
                        onCheckUpdate = onCheckUpdate
                    )
                }
            }

            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))

            if (showEventLog) {
                LogDisplay(
                    events = events,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
