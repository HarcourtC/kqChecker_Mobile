package org.xjtuai.kqchecker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Competition : Screen("competition", "Dean's", Icons.Default.List)
    object Tools : Screen("tools", "Tools", Icons.Default.Build)
    object Integration : Screen("integration", "Integration", Icons.Default.Settings) 
}

@Composable
fun MainScreen(
    events: List<String>,
    onPostEvent: (String) -> Unit,
    onLoginClick: () -> Unit,
    onCheckCacheStatus: () -> Unit,
    onLoginRequired: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    Scaffold(
        bottomBar = {
            BottomNavigation(
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.primary
            ) {
                val screens = listOf(Screen.Home, Screen.Competition, Screen.Tools, Screen.Integration)
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
                        onCheckCacheStatus = onCheckCacheStatus
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
            
            LogDisplay(
                events = events,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}
