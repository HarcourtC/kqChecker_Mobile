package org.xjtuai.kqchecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.xjtuai.kqchecker.repository.WeeklyRepository
import org.xjtuai.kqchecker.ui.components.AppButton
import org.xjtuai.kqchecker.ui.components.InfoCard

@Composable
fun HomeScreen(
    onLoginClick: () -> Unit,
    onCheckCacheStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "kqChecker",
            style = MaterialTheme.typography.h3,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        
        InfoCard(title = "Welcome") {
            Text(text = "Welcome to kqChecker. Please login and check your status below.")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AppButton(
            text = "Login",
            onClick = onLoginClick
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        AppButton(
            text = "Check Cache Status",
            onClick = onCheckCacheStatus,
            backgroundColor = MaterialTheme.colors.secondary
        )
    }
}
