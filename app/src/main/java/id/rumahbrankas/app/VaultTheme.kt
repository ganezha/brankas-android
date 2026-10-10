package id.rumahbrankas.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF10110F)
internal val Panel = Color(0xFF1A1B18)
internal val PanelRaised = Color(0xFF22231F)
internal val Gold = Color(0xFFD3BD82)
internal val Paper = Color(0xFFF3F0E8)
internal val Muted = Color(0xFF9A9A8F)
internal val Line = Color(0xFF35362F)

@Composable
internal fun BrankasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink,
            surface = Panel,
            primary = Gold,
            onPrimary = Ink,
            onBackground = Paper,
            onSurface = Paper,
            secondary = Muted,
        ),
        content = content,
    )
}