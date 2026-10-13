package org.pixeldroid.media_editor.photoEdit

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.common.R as CommonR

/**
 * The whole single-photo path: open an image in the editor, change a slider, tap Save, and check
 * the file the editor reports back: complete, full resolution, and actually edited.
 */
@RunWith(AndroidJUnit4::class)
class PhotoEditSaveTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = TestFolder(context)

    private companion object {
        const val LOAD_TIME_MS = 3000L
    }

    @After fun cleanUp() = folder.deleteAll()

    private fun editorIntent(source: Uri, format: SaveFormat) =
        Intent(context, PhotoEditActivity::class.java)
            .putExtra(PICTURE_URI, source)
            .putExtra(PhotoEditActivity.SAVE_TO_NEW_FILE, true)
            .putExtra(PhotoEditActivity.SAVE_TO_FOLDER, folder.name)
            .putExtra(PhotoEditActivity.SAVE_FORMAT, format.name.lowercase())
            .putExtra(PhotoEditActivity.SAVE_JPEG_QUALITY, 93)

    private fun editAndSave(format: SaveFormat) {
        val source = TestImages.gradient(1600, 1200)
        val sourceUri = TestImages.write(context, "edit-source.png", source)

        ActivityScenario.launchActivityForResult<PhotoEditActivity>(editorIntent(sourceUri, format)).use { scenario ->
            // Same ViewModel instance the activity uses: make the image brighter
            scenario.onActivity { activity ->
                ViewModelProvider(activity, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java]
                    .onBrightnessChange(SliderFragment.BRIGHTNESS_MAX)
            }
            // The image is loaded on the GL thread
            Thread.sleep(LOAD_TIME_MS)
            // Tap "Save". performClick() runs exactly what a tap runs, without simulating touch
            // input (Espresso does that through a hidden Android API removed in newer versions)
            scenario.onActivity { activity ->
                val save = activity.findViewById<View>(CommonR.id.action_save)
                assertNotNull("Save button not shown", save)
                save.performClick()
            }

            val result = scenario.result                              // waits until the editor closes
            assertEquals(Activity.RESULT_OK, result.resultCode)
            val saved = result.resultData.getStringExtra(PICTURE_URI)?.let { Uri.parse(it) }
            assertNotNull("no saved file reported", saved)
            assertTrue("not saved in the test folder", folder.displayName(saved!!) in folder.files().keys)

            val image = ImageIntegrity.assertComplete(context, saved, format, 1600, 1200)
            val before = ImageIntegrity.meanLuminance(source)
            val after = ImageIntegrity.meanLuminance(image)
            assertTrue("edit not applied: brightness $before -> $after", after > before + 10)
        }
    }

    /**
     * A line drawn in the preview ends up in the same place of the saved full resolution image.
     * (Before, drawings were kept in screen pixels and rescaled on saving.)
     */
    @Test fun drawingIsSavedAtTheSamePlace() {
        val source = TestImages.solid(1600, 1200, android.graphics.Color.WHITE)
        val sourceUri = TestImages.write(context, "draw-source.png", source)

        ActivityScenario.launchActivityForResult<PhotoEditActivity>(editorIntent(sourceUri, SaveFormat.PNG)).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java].addStroke(
                    listOf(
                        org.pixeldroid.media_editor.photoEdit.overlay.RelativePoint(0.25f, 0.5f),
                        org.pixeldroid.media_editor.photoEdit.overlay.RelativePoint(0.75f, 0.5f),
                    )
                )
            }
            Thread.sleep(LOAD_TIME_MS)
            scenario.onActivity { activity ->
                activity.findViewById<View>(CommonR.id.action_save).performClick()
            }
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            val saved = result.resultData.getStringExtra(PICTURE_URI)!!.let { Uri.parse(it) }
            val image = ImageIntegrity.assertComplete(context, saved, SaveFormat.PNG, 1600, 1200)
            ImageIntegrity.assertColor(image, 800, 600, android.graphics.Color.RED, 10, "middle of the line")
            ImageIntegrity.assertColor(image, 800, 300, android.graphics.Color.WHITE, 2, "above the line")
            ImageIntegrity.assertColor(image, 200, 600, android.graphics.Color.WHITE, 2, "left of the line")
        }
    }

    @Test fun editedPhotoIsSavedCompletelyAsPng() = editAndSave(SaveFormat.PNG)

    @Test fun editedPhotoIsSavedCompletelyAsJpeg() = editAndSave(SaveFormat.JPEG)

    /** Regression: a truncated image crashed the app instead of opening partially */
    @Test fun truncatedImageOpensWithoutCrash() {
        val uri = TestImages.truncated(context, "truncated.png", TestImages.gradient(1600, 1200), 0.6)
        ActivityScenario.launch<PhotoEditActivity>(
            Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)
        ).use { scenario ->
            Thread.sleep(LOAD_TIME_MS)                              // image is loaded on the GL thread
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
