package com.local.comfyuimobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the current composition lives inside a Glasmio backdrop host.
 *
 * The Glasmio glass controls read `LocalGlassBackdrop`, which throws when no host provides it.
 * The app root installs a host, but a few surfaces live in their own window (a `ComponentDialog`
 * with its own `ComposeView`, a bottom sheet) and therefore never see the page backdrop. Those
 * surfaces render the Material3 form of the same control instead of crashing.
 */
val LocalGlasmioHosted = staticCompositionLocalOf { false }

/**
 * Marks [body] as hosted by a Glasmio backdrop.
 *
 * Only the host itself calls this: everything it contains may use the glass controls.
 */
@Composable
internal fun GlasmioHostedScope(body: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlasmioHosted provides true, content = body)
}
