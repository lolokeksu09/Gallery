package com.lolokeksu.gallery

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A theme carries more than Material's scheme: the grid backdrop, the chrome behind the bars and
 * the card treatment all shift together, so a theme reads as one palette rather than an accent
 * dropped on flat black.
 *
 * Surfaces stay deep enough for an AMOLED panel, but they are tinted rather than pure black; the
 * darkest stop of the backdrop is what sits behind most of the screen.
 */
data class GalleryPalette(
    val id: String,
    val label: String,
    val accent: Color,
    val onAccent: Color,
    val backdropTop: Color,
    val backdropMiddle: Color,
    val backdropBottom: Color,
    val chrome: Color,
    val card: Color,
    val border: Color,
    val muted: Color,
    val warning: Color = Color(0xFFE5C57C),
    val danger: Color = Color(0xFFE59A8C)
) {
    val backdrop: Brush
        get() = Brush.verticalGradient(listOf(backdropTop, backdropMiddle, backdropBottom))

    /** A soft wash of the accent, for hero cards and selected states. */
    val accentWash: Brush
        get() = Brush.verticalGradient(listOf(accent.copy(alpha = .16f), card))

    val colorScheme: ColorScheme
        get() = darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = .20f),
            onPrimaryContainer = accent,
            secondary = accent,
            onSecondary = onAccent,
            background = backdropBottom,
            onBackground = Color(0xFFECEEF2),
            surface = chrome,
            onSurface = Color(0xFFECEEF2),
            surfaceVariant = card,
            onSurfaceVariant = muted,
            outline = border,
            error = danger
        )
}

val GalleryPalettes = listOf(
    GalleryPalette(
        id = "amethyst", label = "Аметист",
        accent = Color(0xFFC0AEFF), onAccent = Color(0xFF1B1440),
        backdropTop = Color(0xFF1C1733), backdropMiddle = Color(0xFF12101F), backdropBottom = Color(0xFF0A0812),
        chrome = Color(0xFF0B0913), card = Color(0xFF15121F), border = Color(0xFF292338),
        muted = Color(0xFF9B94AE)
    ),
    GalleryPalette(
        id = "sunset", label = "Закат",
        accent = Color(0xFFFFA97F), onAccent = Color(0xFF3B1608),
        backdropTop = Color(0xFF2E1A12), backdropMiddle = Color(0xFF1B100B), backdropBottom = Color(0xFF100907),
        chrome = Color(0xFF110A07), card = Color(0xFF1C1310), border = Color(0xFF362520),
        muted = Color(0xFFB4948A)
    ),
    GalleryPalette(
        id = "ocean", label = "Океан",
        accent = Color(0xFF6FD6FF), onAccent = Color(0xFF052C40),
        backdropTop = Color(0xFF0F2739), backdropMiddle = Color(0xFF0A1826), backdropBottom = Color(0xFF050D15),
        chrome = Color(0xFF060E15), card = Color(0xFF0F1A24), border = Color(0xFF1E3040),
        muted = Color(0xFF8CA6B8)
    ),
    GalleryPalette(
        id = "mint", label = "Мята",
        accent = Color(0xFFB3E5CB), onAccent = Color(0xFF123127),
        backdropTop = Color(0xFF17241E), backdropMiddle = Color(0xFF0D1411), backdropBottom = Color(0xFF0A100D),
        chrome = Color(0xFF060A08), card = Color(0xFF0D110F), border = Color(0xFF1E2421),
        muted = Color(0xFF8C948F)
    ),
    GalleryPalette(
        id = "ink", label = "Чернила",
        accent = Color(0xFFF2B8C6), onAccent = Color(0xFF3A1020),
        backdropTop = Color(0xFF241522), backdropMiddle = Color(0xFF150C14), backdropBottom = Color(0xFF0C060B),
        chrome = Color(0xFF0D070C), card = Color(0xFF1A1018), border = Color(0xFF32202C),
        muted = Color(0xFFAE94A4)
    )
)

fun paletteFor(id: String): GalleryPalette =
    GalleryPalettes.firstOrNull { it.id == id } ?: GalleryPalettes.first()

val LocalGalleryPalette = staticCompositionLocalOf { GalleryPalettes.first() }
