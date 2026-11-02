package com.yume.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yume.studio.R
import com.yume.studio.data.ThemeMode

/** Пастельная палитра Yume Studio. */
@Immutable
data class YumePalette(
    val dark: Boolean,
    val background: Color,
    val backgroundAlt: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceLow: Color,
    val outline: Color,
    val primary: Color,
    val primaryDeep: Color,
    val onPrimary: Color,
    val accent: Color,
    val peach: Color,
    val mint: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val danger: Color,
    val keyWhite: Color,
    val keyWhitePressed: Color,
    val keyBlack: Color,
    val keyBlackPressed: Color,
    val gridLine: Color,
    val gridLineStrong: Color,
) {
    /** Цвета дорожек/регионов */
    val tracks: List<Color> = listOf(
        Color(0xFFF08DB4), Color(0xFFF5B35E), Color(0xFFE9C15A), Color(0xFFA985E8),
        Color(0xFFD59BEA), Color(0xFF7FD6B5), Color(0xFFF4978E), Color(0xFF8EC5F2),
    )

    /** Пастельные корпуса педалей (по FxDef.colorIndex) */
    val pedals: List<Color> = listOf(
        Color(0xFF9FE2C9), Color(0xFFB8B0D9), Color(0xFFF6A5C0), Color(0xFFF7B98A),
        Color(0xFFC6A2F2), Color(0xFFF3D27A), Color(0xFF9CCFF5), Color(0xFFD7A6EE),
        Color(0xFFF2A69B), Color(0xFFA6E3B8), Color(0xFFB7B3F5), Color(0xFFFFC7A1),
        Color(0xFFA9D8F2), Color(0xFFF7B7D2), Color(0xFFF1E3C6), Color(0xFF8E6B7D),
        Color(0xFFF59EBB),
    )

    fun track(i: Int) = tracks[i.mod(tracks.size)]
    fun pedal(i: Int) = pedals[i.mod(pedals.size)]
}

val DuskPalette = YumePalette(
    dark = true,
    background = Color(0xFF2B1F27),
    backgroundAlt = Color(0xFF34252F),
    surface = Color(0xFF3E2D38),
    surfaceHigh = Color(0xFF4D3946),
    surfaceLow = Color(0xFF33242D),
    outline = Color(0xFF6E5263),
    primary = Color(0xFFF29BB5),
    primaryDeep = Color(0xFFD9708F),
    onPrimary = Color(0xFF3A1B28),
    accent = Color(0xFFB79CF0),
    peach = Color(0xFFF6B77C),
    mint = Color(0xFF9BE3C4),
    text = Color(0xFFFFF1F5),
    textDim = Color(0xFFD5BAC6),
    textFaint = Color(0xFF9C8090),
    danger = Color(0xFFFF6F8E),
    keyWhite = Color(0xFFFFF6EE),
    keyWhitePressed = Color(0xFFF7C4D4),
    keyBlack = Color(0xFF3A2631),
    keyBlackPressed = Color(0xFFB0607E),
    gridLine = Color(0x22FFFFFF),
    gridLineStrong = Color(0x44FFFFFF),
)

val SakuraPalette = YumePalette(
    dark = false,
    background = Color(0xFFFBE7EE),
    backgroundAlt = Color(0xFFF7DCE6),
    surface = Color(0xFFFFF7F9),
    surfaceHigh = Color(0xFFF6DCE5),
    surfaceLow = Color(0xFFFDEFF3),
    outline = Color(0xFFE5BACB),
    primary = Color(0xFFE07A9A),
    primaryDeep = Color(0xFFC85D80),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF9C7FDB),
    peach = Color(0xFFEFA066),
    mint = Color(0xFF5BC79F),
    text = Color(0xFF4A2E3B),
    textDim = Color(0xFF8E6A7A),
    textFaint = Color(0xFFB896A6),
    danger = Color(0xFFE0436A),
    keyWhite = Color(0xFFFFFFFF),
    keyWhitePressed = Color(0xFFF7C4D4),
    keyBlack = Color(0xFF5A3A4A),
    keyBlackPressed = Color(0xFFC0708E),
    gridLine = Color(0x1A4A2E3B),
    gridLineStrong = Color(0x334A2E3B),
)

val LocalYume = staticCompositionLocalOf { DuskPalette }

private fun nunito(weight: Int) = Font(
    R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val YumeFont = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700), nunito(800), nunito(900))

private val yumeTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = YumeFont, fontWeight = FontWeight.ExtraBold),
        displayMedium = t.displayMedium.copy(fontFamily = YumeFont, fontWeight = FontWeight.ExtraBold),
        displaySmall = t.displaySmall.copy(fontFamily = YumeFont, fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontFamily = YumeFont, fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = YumeFont, fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = YumeFont, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = YumeFont, fontWeight = FontWeight.ExtraBold),
        titleMedium = t.titleMedium.copy(fontFamily = YumeFont, fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontFamily = YumeFont, fontWeight = FontWeight.Bold),
        bodyLarge = t.bodyLarge.copy(fontFamily = YumeFont, fontWeight = FontWeight.SemiBold),
        bodyMedium = t.bodyMedium.copy(fontFamily = YumeFont, fontWeight = FontWeight.SemiBold),
        bodySmall = t.bodySmall.copy(fontFamily = YumeFont, fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontFamily = YumeFont, fontWeight = FontWeight.Bold),
        labelMedium = t.labelMedium.copy(fontFamily = YumeFont, fontWeight = FontWeight.Bold),
        labelSmall = t.labelSmall.copy(fontFamily = YumeFont, fontWeight = FontWeight.Bold),
    )
}

object YumeText {
    val tiny = TextStyle(fontFamily = YumeFont, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    val small = TextStyle(fontFamily = YumeFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val body = TextStyle(fontFamily = YumeFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val title = TextStyle(fontFamily = YumeFont, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    val logo = TextStyle(fontFamily = YumeFont, fontWeight = FontWeight.Black, fontSize = 26.sp)
}

@Composable
fun YumeTheme(mode: ThemeMode = ThemeMode.DUSK, content: @Composable () -> Unit) {
    val pal = when (mode) {
        ThemeMode.DUSK -> DuskPalette
        ThemeMode.SAKURA -> SakuraPalette
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DuskPalette else SakuraPalette
    }
    val scheme = if (pal.dark) {
        darkColorScheme(
            primary = pal.primary, onPrimary = pal.onPrimary, secondary = pal.accent,
            background = pal.background, onBackground = pal.text, surface = pal.surface, onSurface = pal.text,
            surfaceVariant = pal.surfaceHigh, onSurfaceVariant = pal.textDim, outline = pal.outline,
            surfaceContainer = pal.surface, surfaceContainerHigh = pal.surfaceHigh, surfaceContainerHighest = pal.surfaceHigh,
            surfaceContainerLow = pal.surfaceLow, error = pal.danger,
        )
    } else {
        lightColorScheme(
            primary = pal.primary, onPrimary = pal.onPrimary, secondary = pal.accent,
            background = pal.background, onBackground = pal.text, surface = pal.surface, onSurface = pal.text,
            surfaceVariant = pal.surfaceHigh, onSurfaceVariant = pal.textDim, outline = pal.outline,
            surfaceContainer = pal.surface, surfaceContainerHigh = pal.surfaceHigh, surfaceContainerHighest = pal.surfaceHigh,
            surfaceContainerLow = pal.surfaceLow, error = pal.danger,
        )
    }
    CompositionLocalProvider(LocalYume provides pal) {
        MaterialTheme(colorScheme = scheme, typography = yumeTypography, content = content)
    }
}

object Yume {
    val colors: YumePalette
        @Composable get() = LocalYume.current
}
