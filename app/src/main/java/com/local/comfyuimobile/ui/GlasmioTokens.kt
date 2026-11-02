package com.local.comfyuimobile.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlassColors
import io.github.neoruaa.glasmio.core.GlasmioSpacing
import io.github.neoruaa.glasmio.core.GlasmioTheme

/**
 * The Glasmio semantic colors of the current theme.
 *
 * Use these instead of inline literals so a screen keeps the iOS palette in both light and dark
 * mode. Read them inside a composable; [GlasmioTheme.colors] resolves the ambient theme.
 */
@Composable
@ReadOnlyComposable
fun glasmioColors(): GlassColors = GlasmioTheme.colors

/** The continuous corner radii of the current theme. */
@Composable
@ReadOnlyComposable
fun glasmioShapes(): GlasmioShapesToken = GlasmioShapesToken(
    extraSmall = GlasmioTheme.shapes.extraSmall,
    small = GlasmioTheme.shapes.small,
    medium = GlasmioTheme.shapes.medium,
    large = GlasmioTheme.shapes.large,
    extraLarge = GlasmioTheme.shapes.extraLarge,
)

/** The spacing scale of the current theme. */
@Composable
@ReadOnlyComposable
fun glasmioSpacing(): GlasmioSpacing = GlasmioTheme.spacing

/** The Material3 color scheme installed by the Glasmio theme. */
@Composable
@ReadOnlyComposable
fun glasmioMaterialColors(): ColorScheme = androidx.compose.material3.MaterialTheme.colorScheme

/**
 * Corner radii of the current theme, grouped so call sites can pick one by name.
 */
data class GlasmioShapesToken(
    val extraSmall: Shape,
    val small: Shape,
    val medium: Shape,
    val large: Shape,
    val extraLarge: Shape,
)

/**
 * Application semantic colors that Glasmio does not define.
 *
 * Glasmio supplies the destructive red; success and warning are derived from the theme accent so
 * they stay in the same palette in both light and dark mode.
 */
data class GlasmioAppColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
)

/**
 * Reads the application semantic colors for the current theme.
 */
@Composable
@ReadOnlyComposable
fun glasmioAppColors(): GlasmioAppColors {
    val glass = GlasmioTheme.colors
    return GlasmioAppColors(
        success = glass.accent.copy(alpha = 0.9f),
        warning = glass.secondaryContent,
        danger = glass.destructive,
    )
}

/** Height of a standard list row, matching the Glasmio grouped list geometry. */
val GlasmioListRowMinHeight: Dp = 44.dp

/** Horizontal padding shared by page content and list sections. */
val GlasmioPageHorizontalPadding: Dp = 16.dp
