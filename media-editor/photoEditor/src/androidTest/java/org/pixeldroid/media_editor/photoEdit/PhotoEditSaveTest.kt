package org.pixeldroid.media_editor.photoEdit

import android.app.Activity
import android.graphics.Color
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

    /**
     * Runs [block] with an editor that saves and thereby closes itself; [block] waits for that
     * with scenario.result. ActivityScenario.close() waits 45 s for an activity that already
     * finished itself, so the scenario is only closed if the editor is still open (the test
     * failed before it saved). This made each saving test take about 48 s instead of 4 s.
     */
    private fun launchEditorThatSaves(intent: Intent, block: (ActivityScenario<PhotoEditActivity>) -> Unit) {
        val scenario = ActivityScenario.launchActivityForResult<PhotoEditActivity>(intent)
        var editorClosedItself = false
        try {
            block(scenario)
            editorClosedItself = true
        } finally {
            if (!editorClosedItself) scenario.close()
        }
    }

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

        launchEditorThatSaves(editorIntent(sourceUri, format)) { scenario ->
            // Same ViewModel instance the activity uses: make the image brighter
            scenario.onActivity { activity ->
                ViewModelProvider(activity, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java]
                    .onAdjustmentChange(Adjustment.BRIGHTNESS, Adjustment.BRIGHTNESS.max)
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

        launchEditorThatSaves(editorIntent(sourceUri, SaveFormat.PNG)) { scenario ->
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

    /** Top half [top], bottom half [bottom] (or left/right with [sideBySide]) */
    private fun twoColours(top: Int, bottom: Int, sideBySide: Boolean = false): android.graphics.Bitmap =
        android.graphics.Bitmap.createBitmap(1200, 800, android.graphics.Bitmap.Config.ARGB_8888).apply {
            val canvas = android.graphics.Canvas(this)
            canvas.drawColor(top)
            val paint = android.graphics.Paint().apply { color = bottom }
            if (sideBySide) canvas.drawRect(600f, 0f, 1200f, 800f, paint) else canvas.drawRect(0f, 400f, 1200f, 800f, paint)
        }

    /** Saves [source] with [filter] selected and returns the saved image */
    private fun saveWithFilter(source: android.graphics.Bitmap, filter: org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer): android.graphics.Bitmap {
        val sourceUri = TestImages.write(context, "geometry-source.png", source)
        lateinit var image: android.graphics.Bitmap
        launchEditorThatSaves(editorIntent(sourceUri, SaveFormat.PNG)) { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java]
                    .doChange(PhotoEditViewModel.Change.SelectFilter(filter))
            }
            Thread.sleep(LOAD_TIME_MS)
            scenario.onActivity { it.findViewById<View>(CommonR.id.action_save).performClick() }
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            val saved = Uri.parse(result.resultData.getStringExtra(PICTURE_URI)!!)
            image = ImageIntegrity.assertComplete(context, saved, SaveFormat.PNG, 1200, 800)
        }
        return image
    }

    @Test fun mirrorLeftRightKeepsTheLeftHalf() {
        val image = saveWithFilter(twoColours(Color.RED, Color.BLUE, sideBySide = true),
            org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorHorizontalLayer())
        ImageIntegrity.assertColor(image, 300, 400, Color.RED, 4, "left half")
        ImageIntegrity.assertColor(image, 900, 400, Color.RED, 4, "right half shows the left half")
    }

    /** Also checks the image orientation in the shaders: the top must stay on top */
    @Test fun mirrorTopBottomKeepsTheTopHalf() {
        val image = saveWithFilter(twoColours(Color.RED, Color.BLUE),
            org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorVerticalLayer())
        ImageIntegrity.assertColor(image, 600, 200, Color.RED, 4, "top half")
        ImageIntegrity.assertColor(image, 600, 600, Color.RED, 4, "bottom half shows the top half")
    }

    /** The bottom of the image (the ground) in the middle, the top (the sky) outside */
    @Test fun tinyPlanetHasTheGroundInTheMiddle() {
        val image = saveWithFilter(twoColours(Color.RED, Color.BLUE),
            org.pixeldroid.media_editor.photoEdit.imagine.layers.TinyPlanetLayer())
        ImageIntegrity.assertColor(image, 600, 400, Color.BLUE, 4, "centre: bottom of the image")
        ImageIntegrity.assertColor(image, 20, 20, Color.RED, 4, "corner: top of the image")
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
