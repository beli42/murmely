package org.pixeldroid.media_editor.photoEdit

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineView
import org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorVerticalLayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The preview on the screen shows what is saved. Regression: the preview turned the image upside
 * down in its first step, so filters that are not the same top and bottom (kaleidoscope, a
 * centre on the photo, the top–bottom mirror) looked different from the saved image.
 */
@RunWith(AndroidJUnit4::class)
class PreviewMatchesSaveTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** Top half red, bottom half blue */
    private fun redOverBlue() = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until 600) for (x in 0 until 600) setPixel(x, y, if (y < 300) Color.RED else Color.BLUE)
    }

    /** The largest GL view: the preview (the others are tiny ones for thumbnails) */
    private fun previewView(root: View): ImagineView? {
        val found = mutableListOf<ImagineView>()
        fun walk(v: View) {
            if (v is ImagineView) found += v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return found.maxByOrNull { it.width * it.height }
    }

    private fun capture(view: ImagineView): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(view, bitmap, { result = it; done.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue("screen copy timed out", done.await(5, TimeUnit.SECONDS))
        assertTrue("screen copy failed: $result", result == PixelCopy.SUCCESS)
        return bitmap
    }

    private fun isRed(c: Int) = Color.red(c) > 180 && Color.blue(c) < 80
    private fun isBlue(c: Int) = Color.blue(c) > 180 && Color.red(c) < 80

    @Test fun mirrorTopToBottomLooksTheSameOnScreenAndSaved() {
        val uri = TestImages.write(context, "preview-match.png", redOverBlue())
        ActivityScenario.launch<PhotoEditActivity>(Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)).use { scenario ->
            scenario.awaitImageLoaded()
            scenario.onActivity { activity ->
                val model = androidx.lifecycle.ViewModelProvider(activity, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java]
                // Keeps the top part and mirrors it onto the bottom: all red
                model.doChange(PhotoEditViewModel.Change.SelectFilter(MirrorVerticalLayer()))
            }
            // A few frames for the preview to draw
            Thread.sleep(1500)
            lateinit var view: ImagineView
            scenario.onActivity { activity -> view = previewView(activity.window.decorView)!! }
            val screen = capture(view)
            // The photo is in the middle of the view (aspect fit): look at its lower part
            val x = screen.width / 2
            val lower = screen.getPixel(x, screen.height / 2 + screen.width / 4)
            val upper = screen.getPixel(x, screen.height / 2 - screen.width / 4)
            assertTrue("upper part red on screen: ${Integer.toHexString(upper)}", isRed(upper))
            assertTrue("lower part red on screen too (mirrored top), not blue: ${Integer.toHexString(lower)}", isRed(lower) && !isBlue(lower))

            // And the same when saved
            val done = CountDownLatch(1)
            var saved: Bitmap? = null
            scenario.onActivity { activity ->
                val model = androidx.lifecycle.ViewModelProvider(activity, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java]
                activity.renderForTest(listOf(org.pixeldroid.media_editor.photoEdit.imagine.layers.AdjustmentsLayer()) + listOfNotNull(model.filter.value)) {
                    saved = it; done.countDown()
                }
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            val out = saved!!
            assertTrue("saved: lower part red", isRed(out.getPixel(300, 450)))
            assertTrue("saved: upper part red", isRed(out.getPixel(300, 150)))
            assertTrue(abs(out.width - 600) == 0)
        }
    }
}
