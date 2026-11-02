package com.local.comfyuimobile.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.glasmioColorScheme
import io.github.neoruaa.glasmio.core.glasmioTypography

/**
 * Dark palette derived from Glasmio. Kept as an explicit access point because existing
 * screens read the Material3 color scheme directly.
 */
val ComfyMobileDarkColorScheme: ColorScheme = glasmioColorScheme(isDark = true)

/** Light palette derived from Glasmio. */
val ComfyMobileLightColorScheme: ColorScheme = glasmioColorScheme(isDark = false)

/**
 * Applies the Glasmio theme to [content].
 *
 * The theme follows the system light/dark setting. Colors, continuous shapes, spacing and
 * typography come from Glasmio, which installs a Material3 [androidx.compose.material3.MaterialTheme]
 * underneath, so existing Material3 components keep working and gain the continuous corner radii.
 *
 * @param reduceTransparency switches Glasmio to readable opaque surfaces and skips the
 *   backdrop/refraction effects. Intended for the accessibility setting.
 * @param liquidGlassEnabled turns the refraction channel on or off while keeping the blur.
 */
@Composable
fun ComfyMobileTheme(
    reduceTransparency: Boolean = false,
    liquidGlassEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val colors = if (darkTheme) ComfyMobileDarkColorScheme else ComfyMobileLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.background.toArgb()
        }
    }
    GlasmioTheme(
        isDark = darkTheme,
        colorScheme = colors,
        typography = glasmioTypography(),
        appearance = GlassAppearance(
            liquidGlassEnabled = liquidGlassEnabled,
            reduceTransparency = reduceTransparency,
        ),
        content = content,
    )
}
