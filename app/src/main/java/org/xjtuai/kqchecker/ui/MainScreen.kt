package org.xjtuai.kqchecker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.unit.dp
import org.xjtuai.kqchecker.BuildConfig
import org.xjtuai.kqchecker.model.ScheduleItem
import org.xjtuai.kqchecker.network.WaterRecord

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "首页", Icons.Default.Home)
    object Schedule : Screen("schedule", "课表", Icons.Default.DateRange)
    object Competition : Screen("competition", "竞赛", Icons.Default.List)
    object Tools : Screen("tools", "工具", Icons.Default.Build)
    object Integration : Screen("integration", "集成", Icons.Default.Settings)
}

@Composable
fun MainScreen(
    events: List<String>,
    scheduleItems: List<ScheduleItem>,
    latestAttendance: WaterRecord?,
    isLoggedIn: Boolean,
    showEventLog: Boolean,
    onPostEvent: (String) -> Unit,
    onLoginClick: () -> Unit,
    onManualSync: () -> Unit,
    onLoginRequired: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        backgroundColor = MaterialTheme.colors.background,
        bottomBar = {
            BottomNavigation(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.primary,
                elevation = 10.dp
            ) {
                val screens = listOf(Screen.Home, Screen.Schedule, Screen.Competition, Screen.Tools, Screen.Integration)
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
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentScreen) {
                    Screen.Home -> HomeScreen(
                        onLoginClick = onLoginClick,
                        onManualSync = onManualSync,
                        isLoggedIn = isLoggedIn,
                        scheduleItems = scheduleItems,
                        latestAttendance = latestAttendance
                    )
                    Screen.Schedule -> ScheduleScreen(
                        onPostEvent = onPostEvent
                    )
                    Screen.Competition -> CompetitionScreen(
                        onPostEvent = onPostEvent
                    )
                    Screen.Tools -> ToolsScreen(
                        onPostEvent = onPostEvent,
                        onLoginRequired = onLoginRequired
                    )
                    Screen.Integration -> IntegrationScreen(
                        onPostEvent = onPostEvent
                    )
                }
            }

            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))

            if (BuildConfig.DEBUG && showEventLog) {
                LogDisplay(
                    events = events,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
