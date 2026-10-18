package io.github.tytebyte_dev.sf2.ui

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

private val Light = lightColorScheme(
    primary = Color(0xFF5B4FCF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFF8A4FCF),
    tertiary = Color(0xFFCF4F8A),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFC8BFFF),
    onPrimary = Color(0xFF2A1F8F),
    primaryContainer = Color(0xFF41359F),
    secondary = Color(0xFFDDB8FF),
    tertiary = Color(0xFFFFB0D2),
)

@Composable
fun SF2Theme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
