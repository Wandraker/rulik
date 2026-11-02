package app.rulik.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF5C38)
val Good = Color(0xFF3DDC84)
val Warn = Color(0xFFFFB347)
val Bg = Color(0xFF0E1014)
val Card = Color(0xFF171A21)
val Dim = Color(0xFF8A93A6)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF7AA2FF),
    secondaryContainer = Color(0xFF3A2A26),
    onSecondaryContainer = Color(0xFFFFDBD1),
    background = Bg,
    surface = Bg,
    surfaceContainer = Card,
    surfaceContainerHigh = Color(0xFF1E222B),
    surfaceContainerHighest = Color(0xFF252A35),
    surfaceContainerLow = Card,
    onSurface = Color(0xFFE9ECF2),
    onSurfaceVariant = Dim,
    outline = Color(0xFF353B48),
)

@Composable
fun RulikTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
