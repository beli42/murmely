package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.imagine.layers.AdjustmentsLayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * The slider shader (all adjustments in one pass, linear light) rendered by the real engine at
 * full size and measured: unchanged at 0, direction and strength of each slider, grey stays grey.
 */
@RunWith(AndroidJUnit4::class)
class AdjustmentsRenderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** Grey ramp from black (left) to white (right), 512 x 64 */
    private fun greyRamp(): Bitmap = Bitmap.createBitmap(512, 64, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until width) {
            val v = Math.round(x * 255f / (width - 1))
            for (y in 0 until height) setPixel(x, y, Color.rgb(v, v, v))
        }
    }

    /** Opens the editor with [source] and runs [block] with a renderer for slider values */
    private fun withEditor(source: Bitmap, block: (render: (Sliders) -> Bitmap) -> Unit) {
        val uri = TestImages.write(context, "adjustments-source.png", source)
        ActivityScenario.launch<PhotoEditActivity>(
            Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)
        ).use { scenario ->
            scenario.awaitImageLoaded()
            block { sliders ->
                val layer = AdjustmentsLayer().apply { this.sliders = sliders }
                val done = CountDownLatch(1)
                var result: Bitmap? = null
                scenario.onActivity { it.renderForTest(listOf(layer)) { bitmap -> result = bitmap; done.countDown() } }
                assertTrue("rendering timed out", done.await(10, TimeUnit.SECONDS))
                assertNotNull("rendering failed", result)
                assertEquals(source.width to source.height, result!!.width to result!!.height)
                result!!
            }
        }
    }

    private fun Sliders.set(adjustment: Adjustment, normalized: Float) =
        with(adjustment, if (normalized >= 0) normalized * adjustment.max else -normalized * adjustment.min)

    private fun grey(bitmap: Bitmap, x: Int) = Color.red(bitmap.getPixel(x, 32))

    /** Largest difference between the channels of a pixel */
    private fun colourfulness(pixel: Int) =
        max(abs(Color.red(pixel) - Color.green(pixel)), max(abs(Color.green(pixel) - Color.blue(pixel)), abs(Color.red(pixel) - Color.blue(pixel))))

    @Test fun neutralSlidersLeaveTheImageUnchanged() = withEditor(greyRamp()) { render ->
        val source = greyRamp()
        val result = render(Sliders())
        for (x in 0 until 512 step 7) {
            val expected = Color.red(source.getPixel(x, 32))
            assertEquals("grey $expected", expected.toFloat(), grey(result, x).toFloat(), 1f)
        }
    }

    @Test fun lightAdjustmentsInLinearLight() = withEditor(greyRamp()) { render ->
        val black = 0; val mid = 255; val white = 511   // x of black, mid grey (128) and white
        val brighter = render(Sliders().set(Adjustment.EXPOSURE, 0.5f))   // +1 f-stop
        assertTrue("exposure +1 EV: mid grey brighter", grey(brighter, mid) > 128 + 30)
        assertEquals("black stays black", 0f, grey(brighter, black).toFloat(), 1f)

        val darker = render(Sliders().set(Adjustment.EXPOSURE, -0.5f))
        assertTrue("exposure -1 EV: mid grey darker", grey(darker, mid) < 128 - 30)

        val lifted = render(Sliders().set(Adjustment.BRIGHTNESS, 1f))
        assertTrue("brightness: midtones lifted", grey(lifted, mid) > 128 + 30)
        assertEquals("brightness: black stays", 0f, grey(lifted, black).toFloat(), 1f)
        assertEquals("brightness: white stays", 255f, grey(lifted, white).toFloat(), 1f)
        val lowered = render(Sliders().set(Adjustment.BRIGHTNESS, -1f))
        assertTrue("brightness -: midtones lowered", grey(lowered, mid) < 128 - 30)
        assertEquals("brightness -: white stays", 255f, grey(lowered, white).toFloat(), 1f)
    }

    @Test fun toneAdjustments() = withEditor(greyRamp()) { render ->
        val contrast = render(Sliders().set(Adjustment.CONTRAST, 1f))
        assertTrue("contrast: dark greys darker", grey(contrast, 128) < 64 - 20)
        assertTrue("contrast: light greys lighter", grey(contrast, 383) > 191 + 20)
        assertEquals("contrast: middle stays", 128f, grey(contrast, 255).toFloat(), 3f)

        val highlights = render(Sliders().set(Adjustment.HIGHLIGHTS, -1f))
        assertTrue("highlights -: bright greys darker", grey(highlights, 460) < 230 - 15)
        assertEquals("highlights: shadows untouched", 25f, grey(highlights, 50).toFloat(), 2f)

        val shadows = render(Sliders().set(Adjustment.SHADOWS, 1f))
        assertTrue("shadows +: dark greys lighter", grey(shadows, 50) > 25 + 15)
        assertEquals("shadows: highlights untouched", 230f, grey(shadows, 460).toFloat(), 2f)
    }

    /** No adjustment except white balance may tint a grey */
    @Test fun greyStaysGrey() = withEditor(greyRamp()) { render ->
        val neutralOnes = Adjustment.entries - setOf(Adjustment.WARMTH, Adjustment.TINT)
        for (adjustment in neutralOnes) for (value in listOf(-1f, 1f)) {
            if (adjustment.min == 0f && value < 0) continue
            val result = render(Sliders().set(adjustment, value))
            for (x in listOf(40, 128, 255, 383, 470)) {
                assertTrue("$adjustment $value tints grey at x=$x", colourfulness(result.getPixel(x, 32)) <= 2)
            }
        }
    }

    @Test fun whiteBalance() = withEditor(greyRamp()) { render ->
        val warm = render(Sliders().set(Adjustment.WARMTH, 1f)).getPixel(255, 32)
        assertTrue("warmth: more red than blue", Color.red(warm) > Color.blue(warm) + 20)
        val cool = render(Sliders().set(Adjustment.WARMTH, -1f)).getPixel(255, 32)
        assertTrue("cool: more blue than red", Color.blue(cool) > Color.red(cool) + 20)
        val magenta = render(Sliders().set(Adjustment.TINT, 1f)).getPixel(255, 32)
        assertTrue("tint +: less green", Color.green(magenta) < Color.red(magenta) - 10 && Color.green(magenta) < Color.blue(magenta) - 10)
        // Brightness of the grey stays about the same
        val luminance = 0.2125 * Color.red(warm) + 0.7154 * Color.green(warm) + 0.0721 * Color.blue(warm)
        assertEquals("warmth keeps the brightness", 128.0, luminance, 12.0)
    }

    @Test fun colourAdjustments() = withEditor(TestImages.solid(256, 64, Color.rgb(200, 120, 80))) { render ->
        val greyed = render(Sliders().set(Adjustment.SATURATION, -1f)).getPixel(128, 32)
        assertTrue("saturation -100: grey", colourfulness(greyed) <= 2)
        val vivid = render(Sliders().set(Adjustment.SATURATION, 1f)).getPixel(128, 32)
        assertTrue("saturation +: more colourful", colourfulness(vivid) > 120 + 30)
    }

    @Test fun vignetteAndGrain() = withEditor(TestImages.solid(400, 300, Color.rgb(128, 128, 128))) { render ->
        val vignette = render(Sliders().set(Adjustment.VIGNETTE, 1f))
        assertTrue("vignette: corner darker", Color.red(vignette.getPixel(5, 5)) < 128 - 40)
        assertEquals("vignette: centre unchanged", 128f, Color.red(vignette.getPixel(200, 150)).toFloat(), 2f)

        val grain = render(Sliders().set(Adjustment.GRAIN, 1f))
        val values = (0 until 400 step 3).map { Color.red(grain.getPixel(it, 150)) }
        assertTrue("grain: noise", values.maxOrNull()!! - values.minOrNull()!! > 10)
    }

    @Test fun sharpnessIncreasesEdgeContrast() {
        val edge = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.rgb(96, 96, 96))
            canvas.drawRect(150f, 0f, 300f, 300f, Paint().apply { color = Color.rgb(160, 160, 160) })
        }
        withEditor(edge) { render ->
            val sharp = render(Sliders().set(Adjustment.SHARPNESS, 1f))
            assertTrue("dark side of the edge darker", Color.red(sharp.getPixel(149, 150)) < 96 - 5)
            assertTrue("light side of the edge lighter", Color.red(sharp.getPixel(150, 150)) > 160 + 5)
            assertEquals("away from the edge unchanged", 96f, Color.red(sharp.getPixel(50, 150)).toFloat(), 1f)
        }
    }
}
