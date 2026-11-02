package com.local.comfyuimobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.neoruaa.glasmio.core.GlassColors
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost

/**
 * Hosts a page inside a Glasmio backdrop.
 *
 * The sampled background and the glass overlay live in separate layers: the sampled layer records
 * the page background that glass surfaces refract, the overlay layer draws the glass controls. A
 * glass control placed inside the sampled layer would sample itself.
 *
 * Glass controls must be descendants of this host (or of a
 * [io.github.neoruaa.glasmio.glass.GlasmioBackdropProvider]), otherwise reading
 * [io.github.neoruaa.glasmio.glass.LocalGlassBackdrop] throws.
 */
@Composable
fun GlasmioShell(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    GlasmioBackdropHost(
        modifier = modifier.fillMaxSize(),
        sampledContent = { GlasmioBackdropBackground() },
        overlayContent = { content() },
    )
}

/**
 * The page background sampled by the glass layer: the grouped background plus a very light
 * top-to-bottom tint so glass surfaces have something to refract.
 */
@Composable
private fun GlasmioBackdropBackground() {
    val colors = GlasmioTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.groupedBackground)
            .background(glasmioBackdropTint(colors)),
    )
}

/**
 * Derives the backdrop tint from the Glasmio palette instead of hard-coding literals, so the
 * gradient follows the system light/dark setting.
 */
private fun glasmioBackdropTint(colors: GlassColors): Brush {
    val highlight = if (colors.isDark) {
        colors.accent.copy(alpha = 0.08f)
    } else {
        colors.accent.copy(alpha = 0.05f)
    }
    return Brush.verticalGradient(
        0f to highlight,
        0.45f to Color.Transparent,
        1f to colors.secondaryContent.copy(alpha = 0.04f),
    )
}

/**
 * A glass panel for cards and grouped content.
 *
 * This is a thin wrapper over [GlassSurface] with the defaults the application uses, so a page
 * can opt into the liquid glass material without repeating the material parameters.
 */
@Composable
fun GlasmioGlassSurface(
    modifier: Modifier = Modifier,
    emphasis: GlassEmphasis = GlassEmphasis.Regular,
    style: GlassSurfaceStyle = GlassSurfaceStyle.Navigation,
    shape: androidx.compose.ui.graphics.Shape? = null,
    sampleBackdrop: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        style = style,
        emphasis = emphasis,
        shape = shape ?: GlasmioTheme.shapes.large,
        sampleBackdrop = sampleBackdrop,
        onClick = onClick,
        content = content,
    )
}
