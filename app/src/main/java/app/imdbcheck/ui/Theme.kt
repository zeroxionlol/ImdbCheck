package app.imdbcheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ================= „Kinosaal“-Farben =================

object Cine {
    val Night = Color(0xFF0B0B10)
    val Deep = Color(0xFF14141C)
    val Panel = Color(0xFF1A1A24)
    val PanelHigh = Color(0xFF24242F)
    val Line = Color(0xFF34344A)
    val Gold = Color(0xFFF5C518)
    val GoldDim = Color(0xFFB8930F)
    val Red = Color(0xFFFF5A5F)
    val Text = Color(0xFFF2F2F7)
    val TextDim = Color(0xFFA8A8BC)

    /** Farbskala wie bei seriesgraph: dunkelgrün = top, lila = schwach. */
    fun rating(r: Double?): Color = when {
        r == null -> Color(0xFF3A3A48)
        r >= 9.0 -> Color(0xFF1B8E3E)
        r >= 8.0 -> Color(0xFF4CBB5E)
        r >= 7.0 -> Color(0xFFE6C229)
        r >= 6.0 -> Color(0xFFF08A24)
        r >= 5.0 -> Color(0xFFE0482F)
        else -> Color(0xFF8E2C8C)
    }

    /** Gut lesbare Schriftfarbe auf der jeweiligen Wertungsfarbe. */
    fun onRating(r: Double?): Color = when {
        r == null -> TextDim
        r >= 9.0 -> Color.White
        r >= 6.0 -> Color(0xFF111111)
        else -> Color.White
    }

    /** Farben für Staffeln im Verlaufsgraphen. */
    val seasons = listOf(
        Color(0xFF4FC3F7), Color(0xFFF5C518), Color(0xFFFF7A90), Color(0xFF81C784),
        Color(0xFFBA9CFF), Color(0xFFFFA65C), Color(0xFF4DD0C1), Color(0xFFF48FB1),
    )
}

private val scheme = darkColorScheme(
    primary = Cine.Gold,
    onPrimary = Color(0xFF1A1400),
    primaryContainer = Color(0xFF3D3100),
    onPrimaryContainer = Color(0xFFFFE58A),
    secondary = Color(0xFF9EC9FF),
    onSecondary = Color(0xFF00213F),
    secondaryContainer = Color(0xFF233448),
    onSecondaryContainer = Color(0xFFD3E4FF),
    tertiary = Color(0xFF81C784),
    onTertiary = Color(0xFF00210A),
    error = Cine.Red,
    onError = Color(0xFF2B000A),
    errorContainer = Color(0xFF5C0F22),
    onErrorContainer = Color(0xFFFFD9DF),
    background = Color.Transparent,
    onBackground = Cine.Text,
    surface = Color.Transparent,
    onSurface = Cine.Text,
    surfaceVariant = Cine.PanelHigh,
    onSurfaceVariant = Cine.TextDim,
    surfaceTint = Cine.Gold,
    surfaceDim = Cine.Night,
    surfaceBright = Cine.PanelHigh,
    surfaceContainerLowest = Cine.Night,
    surfaceContainerLow = Cine.Deep,
    surfaceContainer = Cine.Panel,
    surfaceContainerHigh = Color(0xFF1F1F2A),
    surfaceContainerHighest = Cine.PanelHigh,
    inverseSurface = Cine.Text,
    inverseOnSurface = Cine.Night,
    inversePrimary = Color(0xFF6E5800),
    outline = Cine.Line,
    outlineVariant = Color(0xFF2A2A38),
    scrim = Color(0xCC000000),
)

private val base = Typography()
private val type = Typography(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Cine.Deep, Cine.Night)))
                .background(
                    Brush.radialGradient(
                        listOf(Cine.Gold.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 1400f,
                    )
                )
        ) { content() }
    }
}
