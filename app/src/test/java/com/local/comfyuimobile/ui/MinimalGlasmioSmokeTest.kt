package com.local.comfyuimobile.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ViewRootForTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Temporary probe: renders one glass wrapper and one segmented choice in the app theme and
 * checks that the composition produces a node tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MinimalGlasmioSmokeTest {

    @Test
    fun rendersGlassWrappersWithoutAHost() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        activity.setContent {
            ComfyMobileTheme(liquidGlassEnabled = false) {
                GlasmioPrimaryButton(onClick = {}, text = "确定")
                StudioChoices(listOf("A", "B"), 0, {})
            }
        }
        val root: ViewRootForTest? = findRoot(activity.window.decorView)
        assertTrue("Compose root missing", root != null)
        root!!.measureAndLayoutForTest()
    }

    private fun findRoot(view: android.view.View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                findRoot(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
