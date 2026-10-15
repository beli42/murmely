package org.pixeldroid.media_editor.photoEdit

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.photoEdit.crop.CropRect
import org.pixeldroid.media_editor.photoEdit.crop.CropRenderer
import org.pixeldroid.media_editor.photoEdit.crop.CropSettings

/** The own crop tool: the cropped file, and the crop in the edit history */
@RunWith(AndroidJUnit4::class)
class CropTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** Lossless, full size, every pixel from the right place */
    @Test fun cropIsTakenFromTheRightPlace() {
        val source = TestImages.gradient(400, 200)
        val uri = TestImages.write(context, "crop-source.png", source)
        // x 100..300, y 100..200
        val cropped = CropRenderer.render(context, uri, CropSettings(CropRect(0.25f, 0.5f, 0.75f, 1f)), context.cacheDir)
        val image = ImageIntegrity.assertComplete(context, cropped, SaveFormat.PNG, 200, 100)
        for (y in 0 until 100 step 7) for (x in 0 until 200 step 11) {
            assertEquals("pixel ($x, $y)", source.getPixel(100 + x, 100 + y), image.getPixel(x, y))
        }
    }

    /** Turned clockwise: the left edge becomes the top */
    @Test fun turnedCropIsTurnedClockwise() {
        val source = TestImages.gradient(400, 200)
        val uri = TestImages.write(context, "crop-turn-source.png", source)
        // Turned once the image is 200 x 400; its top half is the left half of the original
        val cropped = CropRenderer.render(context, uri, CropSettings(CropRect(0f, 0f, 1f, 0.5f), quarterTurns = 1), context.cacheDir)
        val image = ImageIntegrity.assertComplete(context, cropped, SaveFormat.PNG, 200, 200)
        // (x, y) of the result comes from (y, 199 - x) of the original
        for (y in 0 until 200 step 13) for (x in 0 until 200 step 17) {
            assertEquals("pixel ($x, $y)", source.getPixel(y, 199 - x), image.getPixel(x, y))
        }
    }

    @Test fun cropInTheEditHistory() {
        lateinit var model: PhotoEditViewModel
        val original = Uri.parse("file:///test/original.png")
        val cropped = Uri.parse("file:///test/cropped.png")
        val square = CropSettings(CropRect(0.1f, 0.2f, 0.6f, 0.7f), quarterTurns = 1)
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel().apply { initialUri = original }
            // Cancelled: nothing changes
            model.startCrop()
            model.updateCrop(square)
            model.showMain()
        }
        assertNull("draft discarded", model.cropDraft.value)
        assertEquals(0, model.changes.size)

        var draft: CropSettings? = null
        instrumentation.runOnMainSync {
            model.startCrop()
            draft = model.cropDraft.value
            model.finishCrop(cropped, square)
        }
        assertEquals("starts uncropped", CropSettings(), draft)
        assertEquals(cropped, model.imageUri)
        assertEquals(PhotoEditViewModel.ShownView.Main, model.shownView.value)
        assertNull(model.cropDraft.value)

        instrumentation.runOnMainSync {
            // Cropping again starts from the crop made, so it can be adjusted
            model.startCrop()
            draft = model.cropDraft.value
            // Applying it unchanged is no new edit
            model.finishCrop(cropped, square)
        }
        assertEquals(square, draft)
        assertEquals(1, model.changes.size)

        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals(original, model.imageUri)
        assertEquals(CropSettings(), model.recordedCrop)
        instrumentation.runOnMainSync { model.redoChange() }
        assertEquals(square, model.recordedCrop)
    }
}
