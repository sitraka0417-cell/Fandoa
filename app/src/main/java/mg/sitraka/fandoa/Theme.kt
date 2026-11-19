package mg.sitraka.fandoa

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Teal = Color(0xFF0F766E)
val TealDark = Color(0xFF0B4F4A)

private val LightScheme = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Color(0xFFCCF5EF), onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFFB45309), background = Color(0xFFF4F7F7), surface = Color.White,
    surfaceVariant = Color(0xFFE3EBEA), onSurfaceVariant = Color(0xFF3F4948)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4FD8CB), onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFFCCF5EF),
    secondary = Color(0xFFFBBF24), background = Color(0xFF0E1514), surface = Color(0xFF162120),
    surfaceVariant = Color(0xFF2B3735), onSurfaceVariant = Color(0xFFBFC9C7)
)

@Composable
fun FandoaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}

fun Status.color(): Color = when (this) {
    Status.UNPAID -> Color(0xFFD97706)
    Status.PARTIAL -> Color(0xFF2563EB)
    Status.PAID -> Color(0xFF16A34A)
    Status.LATE -> Color(0xFFDC2626)
}
