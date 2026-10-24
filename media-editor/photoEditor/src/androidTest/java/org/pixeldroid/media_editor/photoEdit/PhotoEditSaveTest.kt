package org.pixeldroid.media_editor.photoEdit

import android.app.Activity
import android.graphics.Color
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.pixeldroid.media_editor.photoEdit.crop.CropGeometry
import org.pixeldroid.media_editor.photoEdit.crop.CropSettings
import org.pixeldroid.media_editor.photoEdit.crop.CropTags
import org.pixeldroid.media_editor.photoEdit.crop.StraightenFill
import kotlin.math.roundToInt
import org.pixeldroid.media_editor.photoEdit.panel.PanelTags
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SwirlLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI

/**
 * The whole single-photo path: open an image in the editor, change a slider, tap Save, and check
 * the file the editor reports back: complete, full resolution, and actually edited.
 */
@RunWith(AndroidJUnit4::class)
class PhotoEditSaveTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = TestFolder(context)

    @get:Rule val compose = createEmptyComposeRule()

    @After fun cleanUp() = folder.deleteAll()

    /**
     * Taps "Save": runs its click action directly, without simulating touch input (Espresso does
     * that through a hidden Android API removed in newer versions)
     */
    private fun tapSave() {
        compose.onNodeWithTag(EditorTags.SAVE).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
    }

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
            scenario.awaitImageLoaded()
            tapSave()

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
            scenario.awaitImageLoaded()
            tapSave()
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
    private fun saveWithFilter(
        source: android.graphics.Bitmap,
        filter: org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer,
        settings: FilterSettings = FilterSettings.DEFAULT,
    ): android.graphics.Bitmap {
        val sourceUri = TestImages.write(context, "geometry-source.png", source)
        lateinit var image: android.graphics.Bitmap
        launchEditorThatSaves(editorIntent(sourceUri, SaveFormat.PNG)) { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java]
                    .doChange(PhotoEditViewModel.Change.SelectFilter(filter, settings))
            }
            scenario.awaitImageLoaded()
            tapSave()
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

    /** Tiny planet at strength 0: the photo as it is */
    @Test fun tinyPlanetWithoutStrengthChangesNothing() {
        val image = saveWithFilter(twoColours(Color.RED, Color.BLUE),
            org.pixeldroid.media_editor.photoEdit.imagine.layers.TinyPlanetLayer(), FilterSettings(strength = 0f))
        ImageIntegrity.assertColor(image, 600, 200, Color.RED, 4, "top half")
        ImageIntegrity.assertColor(image, 600, 600, Color.BLUE, 4, "bottom half")
    }

    /** Strength 0: the swirl leaves the image as it is */
    @Test fun swirlWithoutStrengthChangesNothing() {
        // Without strength the point above the centre would be twisted into the bottom (blue) half
        val image = saveWithFilter(twoColours(Color.RED, Color.BLUE), SwirlLayer(), FilterSettings(strength = 0f))
        ImageIntegrity.assertColor(image, 600, 300, Color.RED, 4, "above the centre")
        ImageIntegrity.assertColor(image, 600, 500, Color.BLUE, 4, "below the centre")
    }

    /** The swirl twists around the chosen centre, not the middle of the image */
    @Test fun swirlTwistsAroundTheChosenCentre() {
        val image = saveWithFilter(twoColours(Color.RED, Color.BLUE), SwirlLayer(), FilterSettings(centerX = 0.25f))
        // Centre at (300, 400). With the centre in the middle, (400, 350) would stay red
        ImageIntegrity.assertColor(image, 400, 350, Color.BLUE, 4, "near the new centre: twisted into the bottom half")
        ImageIntegrity.assertColor(image, 600, 300, Color.RED, 4, "middle of the image: hardly twisted")
    }

    /** The mirror line can be moved: right of it, the image is mirrored at that line */
    @Test fun mirrorLineCanBeMoved() {
        val image = saveWithFilter(twoColours(Color.RED, Color.BLUE, sideBySide = true),
            org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorHorizontalLayer(), FilterSettings(centerX = 0.75f))
        ImageIntegrity.assertColor(image, 300, 400, Color.RED, 4, "left of the line unchanged")
        ImageIntegrity.assertColor(image, 750, 400, Color.BLUE, 4, "left of the line unchanged (blue part)")
        ImageIntegrity.assertColor(image, 1100, 400, Color.BLUE, 4, "right of the line: mirrored blue part")
    }

    /**
     * The canvas made larger below: the added area keeps its colour (filters only change the
     * photo), and a caption can be placed on it
     */
    @Test fun canvasWithCaptionIsSaved() {
        val sourceUri = TestImages.write(context, "canvas-source.png", twoColours(Color.RED, Color.BLUE))
        launchEditorThatSaves(editorIntent(sourceUri, SaveFormat.PNG)) { scenario ->
            scenario.awaitImageLoaded()
            compose.onNodeWithTag(EditorTags.tab(EditorTab.EDIT)).performClick()
            compose.onNodeWithTag(PanelTags.CANVAS).performScrollTo().performClick()
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java]
                // 25 % of 800 = 200 px of white below the 1200 x 800 photo
                model.chooseCanvas(
                    org.pixeldroid.media_editor.photoEdit.canvas.CanvasExtension(
                        size = 0.25f, sides = setOf(org.pixeldroid.media_editor.photoEdit.canvas.Side.BOTTOM),
                    )
                )
                // Inverting filter: the photo changes, the added area stays white
                model.doChange(PhotoEditViewModel.Change.SelectFilter(org.pixeldroid.media_editor.photoEdit.imagine.layers.NegativeLayer()))
                // Black caption in the added area (below the photo: y > 1)
                model.onToolStyleChange(model.toolStyle.value.copy(lightness = 0f))
                model.addText("\u2588\u2588\u2588", 0.5f, 1.125f)
            }
            compose.onNodeWithTag(EditorTags.CANVAS).assertExists()
            scenario.awaitImageLoaded()
            tapSave()
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            val saved = Uri.parse(result.resultData.getStringExtra(PICTURE_URI)!!)
            val image = ImageIntegrity.assertComplete(context, saved, SaveFormat.PNG, 1200, 1000)
            ImageIntegrity.assertColor(image, 600, 200, Color.CYAN, 6, "top half: red inverted")
            ImageIntegrity.assertColor(image, 50, 900, Color.WHITE, 2, "added area, not inverted")
            ImageIntegrity.assertColor(image, 600, 900, Color.BLACK, 6, "caption in the added area")
        }
    }

    /** A frame outside makes the saved image larger, the photo stays complete */
    @Test fun frameOutsideIsSaved() {
        val sourceUri = TestImages.write(context, "frame-source.png", twoColours(Color.RED, Color.BLUE))
        launchEditorThatSaves(editorIntent(sourceUri, SaveFormat.PNG)) { scenario ->
            scenario.awaitImageLoaded()
            compose.onNodeWithTag(EditorTags.tab(EditorTab.EDIT)).performClick()
            // The tool row scrolls sideways: the frame tool may be outside at first
            compose.onNodeWithTag(PanelTags.FRAME).performScrollTo().performClick()
            compose.onNodeWithTag(PanelTags.frameStyle(org.pixeldroid.media_editor.photoEdit.frame.FrameStyle.SOLID)).performScrollTo().performClick()
            // Black, 10 %: 80 px around a 1200 x 800 photo
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java]
                model.chooseFrame(model.frame.value!!.copy(size = 0.1f, lightness = 0f))
            }
            compose.onNodeWithTag(EditorTags.FRAME).assertExists()
            tapSave()
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            val saved = Uri.parse(result.resultData.getStringExtra(PICTURE_URI)!!)
            val image = ImageIntegrity.assertComplete(context, saved, SaveFormat.PNG, 1360, 960)
            ImageIntegrity.assertColor(image, 20, 20, Color.BLACK, 4, "frame")
            ImageIntegrity.assertColor(image, 680, 300, Color.RED, 4, "top half of the photo")
            ImageIntegrity.assertColor(image, 680, 700, Color.BLUE, 4, "bottom half of the photo")
        }
    }

    /** Cropping in the editor: turned right, applied and saved at full size */
    @Test fun turnedPhotoIsSaved() {
        val sourceUri = TestImages.write(context, "turn-source.png", twoColours(Color.RED, Color.BLUE, sideBySide = true))
        launchEditorThatSaves(editorIntent(sourceUri, SaveFormat.PNG)) { scenario ->
            scenario.awaitImageLoaded()
            compose.onNodeWithTag(EditorTags.tab(EditorTab.EDIT)).performClick()
            compose.onNodeWithTag(PanelTags.CROP).performClick()
            // The buttons work once the image is shown
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag(CropTags.ROTATE_RIGHT) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag(CropTags.ROTATE_RIGHT).performClick()
            compose.onNodeWithTag(EditorTags.CROP_APPLY).performClick()
            // 1200 x 800 turned
            scenario.awaitImageSize(800, 1200)
            tapSave()
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            val saved = Uri.parse(result.resultData.getStringExtra(PICTURE_URI)!!)
            val image = ImageIntegrity.assertComplete(context, saved, SaveFormat.PNG, 800, 1200)
            // Turned clockwise: the left (red) half is now on top
            ImageIntegrity.assertColor(image, 400, 300, Color.RED, 4, "top")
            ImageIntegrity.assertColor(image, 400, 900, Color.BLUE, 4, "bottom")
        }
    }

    /**
     * Straightened by 30° without filling, the empty corners see-through, saved as [format]:
     * returns the saved image (the box around the turned 1200 x 800 photo)
     */
    private fun straightenSeeThroughAndSave(format: SaveFormat): android.graphics.Bitmap {
        val sourceUri = TestImages.write(context, "straighten-source.png", twoColours(Color.RED, Color.BLUE))
        lateinit var image: android.graphics.Bitmap
        launchEditorThatSaves(editorIntent(sourceUri, format)) { scenario ->
            scenario.awaitImageLoaded()
            compose.onNodeWithTag(EditorTags.tab(EditorTab.EDIT)).performClick()
            compose.onNodeWithTag(PanelTags.CROP).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag(CropTags.STRAIGHTEN) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag(CropTags.STRAIGHTEN).performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
            compose.onNodeWithTag(CropTags.fill(StraightenFill.TRANSPARENT)).performClick()
            compose.onNodeWithTag(EditorTags.CROP_APPLY).performClick()
            val (w, h) = CropGeometry.frameSize(1200f, 800f, CropSettings(angle = 30f, fill = StraightenFill.TRANSPARENT))
            scenario.awaitImageSize(w.roundToInt(), h.roundToInt())
            tapSave()
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            val saved = Uri.parse(result.resultData.getStringExtra(PICTURE_URI)!!)
            image = ImageIntegrity.assertComplete(context, saved, format, w.roundToInt(), h.roundToInt())
            ImageIntegrity.assertColor(image, w.roundToInt() / 2, h.roundToInt() / 2 - 100, Color.RED, 16, "photo in the middle")
        }
        return image
    }

    /** PNG keeps the empty corners of a straightened photo see-through */
    @Test fun straightenedCornersStaySeeThroughInPng() {
        val image = straightenSeeThroughAndSave(SaveFormat.PNG)
        assertTrue("PNG with transparency", image.hasAlpha())
        for ((x, y) in listOf(2 to 2, image.width - 3 to 2, 2 to image.height - 3, image.width - 3 to image.height - 3)) {
            assertEquals("corner ($x, $y) see-through", 0, Color.alpha(image.getPixel(x, y)))
        }
    }

    /** JPEG has no transparency: the see-through corners become white (like empty collage parts) */
    @Test fun straightenedSeeThroughCornersAreWhiteInJpeg() {
        val image = straightenSeeThroughAndSave(SaveFormat.JPEG)
        ImageIntegrity.assertColor(image, 2, 2, Color.WHITE, 8, "corner")
    }

    /** Moved left first: the right part is mirrored onto the left */
    @Test fun mirrorCanKeepTheRightPart() {
        val image = saveWithFilter(twoColours(Color.RED, Color.BLUE, sideBySide = true),
            org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorHorizontalLayer(),
            FilterSettings(mirrorPart = org.pixeldroid.media_editor.photoEdit.imagine.core.types.MirrorPart.END))
        ImageIntegrity.assertColor(image, 300, 400, Color.BLUE, 4, "left: the mirrored right part")
        ImageIntegrity.assertColor(image, 900, 400, Color.BLUE, 4, "right part kept")
    }

    @Test fun editedPhotoIsSavedCompletelyAsPng() = editAndSave(SaveFormat.PNG)

    @Test fun editedPhotoIsSavedCompletelyAsJpeg() = editAndSave(SaveFormat.JPEG)

    /** Regression: a truncated image crashed the app instead of opening partially */
    @Test fun truncatedImageOpensWithoutCrash() {
        val uri = TestImages.truncated(context, "truncated.png", TestImages.gradient(1600, 1200), 0.6)
        ActivityScenario.launch<PhotoEditActivity>(
            Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)
        ).use { scenario ->
            scenario.awaitImageLoaded()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
