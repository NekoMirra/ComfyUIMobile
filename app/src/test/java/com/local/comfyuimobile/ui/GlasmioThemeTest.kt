package com.local.comfyuimobile.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.glasmioColorScheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlasmioThemeTest {
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity

    @After fun tearDown() {
        if (::controller.isInitialized) {
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test fun materialThemeColorSchemeIsAvailableInsideGlasmioTheme() {
        var primary = Color.Unspecified
        show {
            ComfyMobileTheme {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        assertTrue("MaterialTheme.colorScheme must be reachable inside GlasmioTheme", primary != Color.Unspecified)
    }

    @Test fun glasmioShapesUseContinuousRoundedCorners() {
        val shapes = mutableListOf<Shape>()
        show {
            ComfyMobileTheme {
                val glasmioShapes = GlasmioTheme.shapes
                shapes += listOf(
                    glasmioShapes.extraSmall,
                    glasmioShapes.small,
                    glasmioShapes.medium,
                    glasmioShapes.large,
                    glasmioShapes.extraLarge,
                )
            }
        }
        assertEquals("every Glasmio shape must be present", 5, shapes.size)
        shapes.forEach { shape ->
            assertTrue("shape must not be a plain rectangle: $shape", shape != RectangleShape)
            assertTrue(
                "shape must be a continuous rounded rectangle: $shape",
                shape::class.java.name.contains("ContinuousRoundedRectangle"),
            )
        }
    }

    @Test fun lightAndDarkSchemesDiffer() {
        assertNotEquals(
            "the light and dark Glasmio palettes must differ",
            ComfyMobileLightColorScheme.primary,
            ComfyMobileDarkColorScheme.primary,
        )
        assertEquals(
            "the dark palette must match glasmioColorScheme(true)",
            glasmioColorScheme(isDark = true).primary,
            ComfyMobileDarkColorScheme.primary,
        )
        assertEquals(
            "the light palette must match glasmioColorScheme(false)",
            glasmioColorScheme(isDark = false).primary,
            ComfyMobileLightColorScheme.primary,
        )
    }

    @Test fun glasmioShellRendersContentWithoutThrowing() {
        var rendered = false
        show {
            ComfyMobileTheme {
                GlasmioShell {
                    Box(Modifier.fillMaxSize()) {
                        Text("玻璃外壳")
                        rendered = true
                    }
                }
            }
        }
        assertTrue("GlasmioShell must render its content", rendered)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity = controller.get()
        activity.setContent { content() }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
