package com.lolokeksu.gallery

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material 3 on an AMOLED panel: the page itself is true black, and everything that is not media
 * sits on the tonal container ramp above it. A theme is one accent plus the three container tones
 * tinted towards that accent's hue, so the whole interface shifts together instead of an accent
 * being dropped on flat grey.
 *
 * Black is deliberate here. Behind photographs it costs no light on the panel and lets the media
 * define the colour of the screen; the interface earns its tint only where it is actually drawn —
 * bars, cards, menus, selection.
 */
data class GalleryPalette(
    val id: String,
    val label: String,
    /** Material's primary: tone 80 of the theme's hue, legible on black. */
    val accent: Color,
    val onAccent: Color,
    /** The page. Pure black on every theme. */
    val backdrop: Color,
    /** surfaceContainerLow — settings groups and thumbnail placeholders. */
    val card: Color,
    /** surfaceContainer — the navigation bar and a top bar with content scrolled under it. */
    val chrome: Color,
    /** surfaceContainerHigh — menus, dialogs, pressed and selected rows. */
    val elevated: Color,
    val border: Color,
    val muted: Color,
    val warning: Color = Color(0xFFE5C57C),
    val danger: Color = Color(0xFFE59A8C)
) {
    val colorScheme: ColorScheme
        get() = darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = elevated,
            onPrimaryContainer = accent,
            inversePrimary = accent,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = elevated,
            onSecondaryContainer = accent,
            tertiary = accent,
            onTertiary = onAccent,
            tertiaryContainer = elevated,
            onTertiaryContainer = accent,
            background = backdrop,
            onBackground = ON_SURFACE,
            surface = backdrop,
            onSurface = ON_SURFACE,
            surfaceVariant = card,
            onSurfaceVariant = muted,
            surfaceContainerLowest = backdrop,
            surfaceContainerLow = card,
            surfaceContainer = chrome,
            surfaceContainerHigh = elevated,
            surfaceContainerHighest = elevated,
            surfaceTint = accent,
            outline = border,
            outlineVariant = border,
            scrim = Color.Black,
            error = danger,
            onError = Color(0xFF3A0B05),
            errorContainer = elevated,
            onErrorContainer = danger,
            inverseSurface = ON_SURFACE,
            inverseOnSurface = backdrop
        )
}

/** Material's dark onSurface. Shared: the text stays neutral so the accent keeps its weight. */
private val ON_SURFACE = Color(0xFFE6E1E9)

private val BLACK = Color(0xFF000000)

val GalleryPalettes = listOf(
    GalleryPalette(
        id = "amethyst", label = "Аметист",
        accent = Color(0xFFCFBCFF), onAccent = Color(0xFF291D4C),
        backdrop = BLACK,
        card = Color(0xFF121016), chrome = Color(0xFF17141C), elevated = Color(0xFF211D28),
        border = Color(0xFF2B2534), muted = Color(0xFF9A93A6)
    ),
    GalleryPalette(
        id = "sunset", label = "Закат",
        accent = Color(0xFFFFB68E), onAccent = Color(0xFF4A1F07),
        backdrop = BLACK,
        card = Color(0xFF16100C), chrome = Color(0xFF1C1511), elevated = Color(0xFF271E19),
        border = Color(0xFF34291F), muted = Color(0xFFB09287)
    ),
    GalleryPalette(
        id = "ocean", label = "Океан",
        accent = Color(0xFF7AD6FF), onAccent = Color(0xFF00344A),
        backdrop = BLACK,
        card = Color(0xFF0B1116), chrome = Color(0xFF10171C), elevated = Color(0xFF192228),
        border = Color(0xFF22303A), muted = Color(0xFF8FA6B4)
    ),
    GalleryPalette(
        id = "mint", label = "Мята",
        accent = Color(0xFF8FE0B4), onAccent = Color(0xFF003822),
        backdrop = BLACK,
        card = Color(0xFF0C1310), chrome = Color(0xFF111915), elevated = Color(0xFF1A241E),
        border = Color(0xFF243029), muted = Color(0xFF8FA396)
    ),
    GalleryPalette(
        id = "ink", label = "Чернила",
        accent = Color(0xFFFFB1C8), onAccent = Color(0xFF511F33),
        backdrop = BLACK,
        card = Color(0xFF161013), chrome = Color(0xFF1C1519), elevated = Color(0xFF261D22),
        border = Color(0xFF332530), muted = Color(0xFFAC94A0)
    )
)

fun paletteFor(id: String): GalleryPalette =
    GalleryPalettes.firstOrNull { it.id == id } ?: GalleryPalettes.first()

val LocalGalleryPalette = staticCompositionLocalOf { GalleryPalettes.first() }
