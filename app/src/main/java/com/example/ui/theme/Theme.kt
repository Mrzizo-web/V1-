package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GatewayPrimary,
    onPrimary = GatewayOnPrimary,
    primaryContainer = GatewayPrimaryContainer,
    onPrimaryContainer = GatewayOnPrimaryContainer,
    secondary = GatewaySecondary,
    onSecondary = GatewayOnSecondary,
    background = GatewayBackgroundDark,
    surface = GatewaySurfaceDark,
    surfaceVariant = GatewaySurfaceVariantDark,
    onBackground = GatewayOnSurfaceDark,
    onSurface = GatewayOnSurfaceDark,
    onSurfaceVariant = GatewayOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFC78100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDE9F),
    onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF00838F),
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECEF),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF495057)
)

@Composable
fun Theme(
    darkTheme: Boolean = true, // Default to sleek dark mode for industrial POS gateway
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = Theme(darkTheme, dynamicColor, content)
