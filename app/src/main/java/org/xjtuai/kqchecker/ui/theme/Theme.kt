package org.xjtuai.kqchecker.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColorPalette = lightColors(
    primary = BluePrimary,
    primaryVariant = BlueVariant,
    secondary = Secondary,
    background = Background,
    surface = Surface,
    error = Error,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onBackground = OnBackground,
    onSurface = OnSurface,
    onError = OnError
)

@Composable
fun KqCheckerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = LightColorPalette,
        typography = Typography(
            body1 = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            ),
            h5 = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp
            ),
            h6 = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp
            ),
            button = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        ),
        shapes = MaterialTheme.shapes,
        content = content
    )
}
