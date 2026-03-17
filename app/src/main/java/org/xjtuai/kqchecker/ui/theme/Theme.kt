package org.xjtuai.kqchecker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColorPalette = lightColors(
    primary = OceanPrimary,
    primaryVariant = OceanPrimaryVariant,
    secondary = OceanSecondary,
    background = OceanBackground,
    surface = OceanSurface,
    error = OceanError,
    onPrimary = OceanOnPrimary,
    onSecondary = OceanOnSecondary,
    onBackground = OceanOnBackground,
    onSurface = OceanOnSurface,
    onError = OceanOnError
)

private val DarkColorPalette = darkColors(
    primary = OceanPrimaryDark,
    primaryVariant = OceanPrimaryVariantDark,
    secondary = OceanSecondaryDark,
    background = OceanBackgroundDark,
    surface = OceanSurfaceDark,
    error = OceanErrorDark,
    onPrimary = OceanOnPrimaryDark,
    onSecondary = OceanOnSecondaryDark,
    onBackground = OceanOnBackgroundDark,
    onSurface = OceanOnSurfaceDark,
    onError = OceanOnErrorDark
)

@Composable
fun KqCheckerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    val appTypography = Typography(
        h3 = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp,
            letterSpacing = (-0.5).sp
        ),
        h5 = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            letterSpacing = 0.sp
        ),
        h6 = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = 0.15.sp
        ),
        subtitle1 = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            letterSpacing = 0.15.sp
        ),
        body1 = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            letterSpacing = 0.5.sp
        ),
        body2 = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            letterSpacing = 0.25.sp
        ),
        caption = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 0.4.sp
        ),
        button = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.5.sp
        )
    )

    MaterialTheme(
        colors = colors,
        typography = appTypography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
