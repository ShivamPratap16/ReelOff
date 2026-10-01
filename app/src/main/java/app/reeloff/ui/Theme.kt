package app.reeloff.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF4F46E5)
private val IndigoLight = Color(0xFFA5B4FC)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0D9488),
    tertiary = Color(0xFFDB2777),
    background = Color(0xFFF8F7FC),
    surface = Color(0xFFF8F7FC),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF5EEAD4),
    tertiary = Color(0xFFF9A8D4),
    background = Color(0xFF121218),
    surface = Color(0xFF121218),
)

@Composable
fun ReelOffTheme(content: @Composable () -> Unit) {
    // Brand colours rather than dynamic colour: a blocker should look the same every time you meet it.
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
