package io.github.genowhirl.catchup.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.genowhirl.catchup.R

// Warm coral brand palette (Material 3 roles, derived from #D9603B).
private val Light = lightColorScheme(
    primary = Color(0xFFA6431F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF380D00),
    secondary = Color(0xFF77574C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C160D),
    tertiary = Color(0xFF3F6A4B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC0F0CB),
    onTertiaryContainer = Color(0xFF00210E),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF231915),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF231915),
    surfaceVariant = Color(0xFFF5DED6),
    onSurfaceVariant = Color(0xFF53433E),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C2BA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFCEAE4),
    surfaceContainerHigh = Color(0xFFF6E4DE),
    surfaceContainerHighest = Color(0xFFF1DFD8),
    inverseSurface = Color(0xFF392E2A),
    inverseOnSurface = Color(0xFFFFEDE7),
    inversePrimary = Color(0xFFFFB59C),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB59C),
    onPrimary = Color(0xFF5B1A00),
    primaryContainer = Color(0xFF842C09),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF5D4035),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFA4D4B0),
    onTertiary = Color(0xFF0C3920),
    tertiaryContainer = Color(0xFF275135),
    onTertiaryContainer = Color(0xFFC0F0CB),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A120F),
    onBackground = Color(0xFFF1DFD8),
    surface = Color(0xFF1A120F),
    onSurface = Color(0xFFF1DFD8),
    surfaceVariant = Color(0xFF53433E),
    onSurfaceVariant = Color(0xFFD8C2BA),
    outline = Color(0xFFA08D86),
    outlineVariant = Color(0xFF53433E),
    surfaceContainerLowest = Color(0xFF140C0A),
    surfaceContainerLow = Color(0xFF231915),
    surfaceContainer = Color(0xFF271D19),
    surfaceContainerHigh = Color(0xFF322823),
    surfaceContainerHighest = Color(0xFF3D322E),
    inverseSurface = Color(0xFFF1DFD8),
    inverseOnSurface = Color(0xFF392E2A),
    inversePrimary = Color(0xFFA6431F),
)

/** Extra colours for statuses and reason chips. */
@Immutable
data class CatchUpColors(
    val birthday: Color,
    val onBirthday: Color,
    val overdue: Color,
    val onOverdue: Color,
    val dueSoon: Color,
    val onDueSoon: Color,
    val ok: Color,
    val onOk: Color,
)

private val LightExtra = CatchUpColors(
    birthday = Color(0xFFFFDBCF), onBirthday = Color(0xFF842C09),
    overdue = Color(0xFFFFDAD6), onOverdue = Color(0xFF93000A),
    dueSoon = Color(0xFFFFE08F), onDueSoon = Color(0xFF5B4300),
    ok = Color(0xFFC0F0CB), onOk = Color(0xFF275135),
)
private val DarkExtra = CatchUpColors(
    birthday = Color(0xFF842C09), onBirthday = Color(0xFFFFDBCF),
    overdue = Color(0xFF93000A), onOverdue = Color(0xFFFFDAD6),
    dueSoon = Color(0xFF5B4300), onDueSoon = Color(0xFFFFE08F),
    ok = Color(0xFF275135), onOk = Color(0xFFC0F0CB),
)

val LocalCatchUpColors = staticCompositionLocalOf { LightExtra }

private fun fraunces(weight: Int) = Font(
    R.font.fraunces,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", 72f),
        FontVariation.Setting("SOFT", 100f),
    ),
)

private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(fraunces(400), fraunces(600), fraunces(700))
val Body = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private val base = Typography()

val CatchUpTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    displayMedium = base.displayMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    displaySmall = base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Body),
    bodyMedium = base.bodyMedium.copy(fontFamily = Body),
    bodySmall = base.bodySmall.copy(fontFamily = Body),
    labelLarge = base.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
)

/** Small all-caps section labels. */
val SectionLabel = TextStyle(fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.2.sp)

val CatchUpShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun CatchUpTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = false, content: @Composable () -> Unit) {
    val colors: ColorScheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalCatchUpColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = colors, typography = CatchUpTypography, shapes = CatchUpShapes, content = content)
    }
}

object CatchUpTheme {
    val colors: CatchUpColors @Composable get() = LocalCatchUpColors.current
}
