package org.pixeldroid.media_editor.photoEdit

import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.photoEdit.collage.CollageTags
import org.pixeldroid.media_editor.photoEdit.collage.CollageViewModel
import org.pixeldroid.media_editor.photoEdit.ui.main.CollageActivity

/** The collage screens (Compose) and the whole flow of the ViewModel up to the saved file */
@RunWith(AndroidJUnit4::class)
class CollageScreenTest {

    @get:Rule val compose = createAndroidComposeRule<CollageActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val folder = TestFolder(context)

    @After fun cleanUp() = folder.deleteAll()

    /** One page: the layouts on top, the collage below with its numbered areas */
    @Test fun allLayoutsAreOfferedOnThePageWithTheCollage() {
        for (layout in CollageGeometry.LAYOUTS) compose.onNodeWithTag(CollageTags.layout(layout)).assertIsDisplayed()
        // The grid is chosen at first, with its areas and the button for several photos
        for (area in 1..4) compose.onNodeWithTag(CollageTags.area(area)).assertExists()
        compose.onNodeWithTag(CollageTags.PICK_MANY).assertExists()
    }

    @Test fun choosingALayoutShowsItsAreas() {
        compose.onNodeWithTag(CollageTags.layout(4)).performClick()
        for (area in 1..4) compose.onNodeWithTag(CollageTags.area(area)).assertIsDisplayed()
        // Nothing to save without images
        compose.onNodeWithTag(CollageTags.SAVE).assertIsNotEnabled()
    }

    @Test fun theNewGridHasNineAreas() {
        compose.onNodeWithTag(CollageTags.layout(5)).performClick()
        for (area in 1..9) compose.onNodeWithTag(CollageTags.area(area)).assertExists()
        compose.onNodeWithTag(CollageTags.area(10)).assertDoesNotExist()
    }

    /** Another layout keeps the photos in their order, as far as it has areas for them */
    @Test fun changingTheLayoutKeepsThePhotosInOrder() {
        val a = TestImages.write(context, "collage-a.png", TestImages.solid(100, 100, Color.RED))
        val b = TestImages.write(context, "collage-b.png", TestImages.solid(100, 100, Color.GREEN))
        val c = TestImages.write(context, "collage-c.png", TestImages.solid(100, 100, Color.BLUE))
        lateinit var viewModel: CollageViewModel
        instrumentation.runOnMainSync {
            viewModel = CollageViewModel(compose.activity.application, SavedStateHandle())
            viewModel.chooseLayout(5)
            viewModel.imagesPicked(listOf(a, b, c))
        }
        // 3 x 3: from the top left clockwise
        assertEquals(mapOf(1 to a, 2 to b, 3 to c), viewModel.state.value.images)
        instrumentation.runOnMainSync { viewModel.chooseLayout(4) }
        // 2 x 2: first, second, third in its order 1, 2, 4
        assertEquals(mapOf(1 to a, 2 to b, 4 to c), viewModel.state.value.images)
        instrumentation.runOnMainSync { viewModel.chooseLayout(2) }
        assertEquals("two areas: the first two", mapOf(1 to a, 2 to b), viewModel.state.value.images)
    }

    /** The ViewModel from choosing a layout to the saved file, checked like the other saved files */
    @Test fun collageIsSavedCompletelyWithTheDateName() {
        val red = TestImages.write(context, "collage-red.png", TestImages.solid(800, 600, Color.RED))
        val blue = TestImages.write(context, "collage-blue.png", TestImages.solid(600, 800, Color.BLUE))
        lateinit var viewModel: CollageViewModel
        instrumentation.runOnMainSync {
            viewModel = CollageViewModel(
                compose.activity.application,
                SavedStateHandle(mapOf(
                    PhotoEditActivity.SAVE_TO_FOLDER to folder.name,
                    PhotoEditActivity.SAVE_FORMAT to "png",
                )),
            )
            viewModel.chooseLayout(2)
            viewModel.pickImageFor(1)
            viewModel.imagePicked(red)
            viewModel.pickImageFor(2)
            viewModel.imagePicked(blue)
            viewModel.save()
        }
        var result: Uri? = null
        val deadline = System.currentTimeMillis() + 30_000
        while (result == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            result = viewModel.state.value.result
        }
        assertNotNull("collage was not saved", result)
        assertNotNull("preview was rendered", viewModel.state.value.preview)

        assertEquals("collage-${FileNaming.today()}.png", folder.displayName(result!!))
        val saved = ImageIntegrity.assertComplete(context, result, SaveFormat.PNG, CollageRenderer.SIZE, CollageRenderer.SIZE)
        // Default mode STRETCH: left half red, right half blue
        ImageIntegrity.assertColor(saved, 500, 1000, Color.RED, 4, "left area")
        ImageIntegrity.assertColor(saved, 1500, 1000, Color.BLUE, 4, "right area")
        assertTrue("only one file", folder.files().size == 1)
    }
}
