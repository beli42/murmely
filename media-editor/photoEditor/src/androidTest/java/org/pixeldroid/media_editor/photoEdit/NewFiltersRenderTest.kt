package org.pixeldroid.media_editor.photoEdit

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ConfiguredFilter
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.AdjustmentsLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.CinemaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.GlassTilesLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.PopLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.RippleLayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** The four new filters: Pop, Cinema, Ripple, Glass tiles */
@RunWith(AndroidJUnit4::class)
class NewFiltersRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** [filter] applied to [source] at full size, after neutral sliders (as in the editor) */
    private fun withEditor(source: Bitmap, block: (render: (ImagineLayer) -> Bitmap) -> Unit) {
        val uri = TestImages.write(context, "new-filters-source.png", source)
        ActivityScenario.launch<PhotoEditActivity>(
            Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)
        ).use { scenario ->
            scenario.awaitImageLoaded()
            block { filter ->
                val done = CountDownLatch(1)
                var result: Bitmap? = null
                scenario.onActivity { it.renderForTest(listOf(AdjustmentsLayer(), filter)) { bitmap -> result = bitmap; done.countDown() } }
                assertTrue("rendering timed out", done.await(10, TimeUnit.SECONDS))
                assertNotNull("rendering failed", result)
                result!!
            }
        }
    }

    private fun saturation(c: Int) = maxOf(Color.red(c), Color.green(c), Color.blue(c)) - minOf(Color.red(c), Color.green(c), Color.blue(c))

    @Test fun popMakesMutedColoursVivid() {
        val muted = Color.rgb(150, 120, 110)
        withEditor(TestImages.solid(200, 200, muted)) { render ->
            val out = render(PopLayer()).getPixel(100, 100)
            assertTrue("more saturated: ${Integer.toHexString(out)}", saturation(out) > saturation(muted) + 15)
        }
    }

    @Test fun cinemaWarmsHighlightsAndCoolsShadows() {
        withEditor(TestImages.solid(200, 200, Color.rgb(215, 215, 215))) { render ->
            val light = render(CinemaLayer()).getPixel(100, 100)
            assertTrue("light grey turns warm: ${Integer.toHexString(light)}", Color.red(light) > Color.blue(light) + 10)
        }
        withEditor(TestImages.solid(200, 200, Color.rgb(45, 45, 45))) { render ->
            val dark = render(CinemaLayer()).getPixel(100, 100)
            assertTrue("dark grey turns teal: ${Integer.toHexString(dark)}", Color.blue(dark) > Color.red(dark) + 3)
        }
    }

    /** Black and white squares of [square] pixels: every small shift shows */
    private fun checkerboard(width: Int, height: Int, square: Int) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) for (x in 0 until width) {
            setPixel(x, y, if ((x / square + y / square) % 2 == 0) Color.BLACK else Color.WHITE)
        }
    }

    /** Geometric filters: at strength 0 the image is unchanged, at full strength it is moved around */
    @Test fun rippleAndGlassTilesMoveTheImageWithTheirStrength() {
        // A checkerboard rather than a smooth gradient: Ripple moves pixels by only a few
        // pixels, which hardly changes a gradient's colour
        val source = checkerboard(600, 400, 8)
        withEditor(source) { render ->
            for (filter in listOf(RippleLayer(), GlassTilesLayer())) {
                val none = render(ConfiguredFilter(filter, FilterSettings(strength = 0f)))
                val full = render(ConfiguredFilter(filter, FilterSettings(strength = 1f)))
                var changedAtZero = 0
                var changedAtFull = 0
                for (y in 0 until 400 step 10) for (x in 0 until 600 step 10) {
                    if (differs(none.getPixel(x, y), source.getPixel(x, y))) changedAtZero++
                    if (differs(full.getPixel(x, y), source.getPixel(x, y))) changedAtFull++
                }
                val name = filter.javaClass.simpleName
                assertTrue("$name at 0 changes nothing ($changedAtZero)", changedAtZero < 20)
                assertTrue("$name at full strength changes the image ($changedAtFull)", changedAtFull > 200)
            }
        }
    }

    /** Contrast of a 16 x 16 patch: high where sharp (black and white squares), low where blurred */
    private fun contrast(b: Bitmap, cx: Int, cy: Int): Int {
        var lo = 255; var hi = 0
        for (y in cy - 8 until cy + 8) for (x in cx - 8 until cx + 8) {
            val v = Color.red(b.getPixel(x, y)); lo = minOf(lo, v); hi = maxOf(hi, v)
        }
        return hi - lo
    }

    @Test fun focusIsSharpAtItsPointAndBlurredAway() {
        withEditor(checkerboard(600, 400, 8)) { render ->
            val image = render(ConfiguredFilter(org.pixeldroid.media_editor.photoEdit.imagine.layers.FocusLayer(), FilterSettings(strength = 1f)))
            assertTrue("sharp in the middle: ${contrast(image, 300, 200)}", contrast(image, 300, 200) > 200)
            assertTrue("blurred in the corner: ${contrast(image, 40, 40)}", contrast(image, 40, 40) < 120)
            val none = render(ConfiguredFilter(org.pixeldroid.media_editor.photoEdit.imagine.layers.FocusLayer(), FilterSettings(strength = 0f)))
            assertTrue("strength 0: sharp everywhere", contrast(none, 40, 40) > 200)
        }
    }

    @Test fun tiltShiftIsSharpInItsBandAndBlurredAboveAndBelow() {
        withEditor(checkerboard(600, 400, 8)) { render ->
            // Band at a quarter of the height
            val image = render(ConfiguredFilter(org.pixeldroid.media_editor.photoEdit.imagine.layers.TiltShiftLayer(), FilterSettings(strength = 1f, centerY = 0.25f)))
            assertTrue("sharp in the band", contrast(image, 300, 100) > 200)
            assertTrue("sharp along the band, also at the side", contrast(image, 40, 100) > 200)
            assertTrue("blurred far below: ${contrast(image, 300, 360)}", contrast(image, 300, 360) < 120)
        }
    }

    /** A smaller area changes less of the image, a larger one more */
    @Test fun sizeOfTheAreaMatters() {
        val source = checkerboard(600, 400, 8)
        withEditor(source) { render ->
            fun changed(size: Float): Int {
                val image = render(ConfiguredFilter(org.pixeldroid.media_editor.photoEdit.imagine.layers.SwirlLayer(), FilterSettings(size = size)))
                var n = 0
                for (y in 0 until 400 step 10) for (x in 0 until 600 step 10) if (differs(image.getPixel(x, y), source.getPixel(x, y))) n++
                return n
            }
            val small = changed(0.4f)
            val normal = changed(1f)
            val large = changed(2f)
            assertTrue("small $small < normal $normal < large $large", small < normal && normal < large)
        }
    }

    private fun differs(a: Int, b: Int) =
        abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b)) > 12
}
