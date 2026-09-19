package com.openclassrooms.rebonnte.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

private const val THEME_PREFERENCES = "theme_preferences"
private const val DARK_THEME_KEY = "dark_theme_enabled"

fun Context.isDarkThemeEnabled(): Boolean {
    val systemUsesDarkTheme = resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    return getSharedPreferences(THEME_PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(DARK_THEME_KEY, systemUsesDarkTheme)
}

fun Context.setDarkThemeEnabled(enabled: Boolean) {
    getSharedPreferences(THEME_PREFERENCES, Context.MODE_PRIVATE)
        .edit {
          putBoolean(DARK_THEME_KEY, enabled)
        }
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun RebonnteTheme(
    darkTheme: Boolean? = null,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDarkTheme = darkTheme ?: context.isDarkThemeEnabled()

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
