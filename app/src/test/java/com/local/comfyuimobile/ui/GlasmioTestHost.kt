package com.local.comfyuimobile.ui

import androidx.compose.runtime.Composable

/**
 * Renders [content] the way the UI tests need it.
 *
 * The app root installs a Glasmio backdrop host, but Robolectric has no real graphics pipeline:
 * the glass material keeps the frame clock busy forever, so the test would never reach an idle
 * state and the run would hang instead of finishing. The tests therefore stay unhosted, which
 * makes every glass wrapper render its Material3 fallback form. That is deliberate: the UI tests
 * assert the adaptive layout and the settings behaviour, not the glass rendering.
 */
@Composable
internal fun GlasmioTestHost(content: @Composable () -> Unit) {
    content()
}
