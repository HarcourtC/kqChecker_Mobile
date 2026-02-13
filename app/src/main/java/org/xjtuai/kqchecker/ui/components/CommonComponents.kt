package org.xjtuai.kqchecker.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.primary
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(backgroundColor = backgroundColor)
    ) {
        Text(text = text)
    }
}

@Composable
fun InfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        elevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}
