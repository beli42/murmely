package org.pixeldroid.media_editor.photoEdit

import android.content.Intent
import android.graphics.Color
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.info.ImageInfoReader

/** Information about the opened photo */
@RunWith(AndroidJUnit4::class)
class ImageInfoTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun readsNameSizeFormatAndResolution() {
        val uri = TestImages.write(context, "info-test.png", TestImages.solid(640, 480, Color.RED))
        val info = ImageInfoReader.read(context, uri)
        assertEquals("info-test.png", info.name)
        assertEquals("PNG", info.format)
        assertEquals(640, info.width)
        assertEquals(480, info.height)
        assertTrue("file size known: ${info.bytes}", (info.bytes ?: 0) > 0)
        assertTrue("colour space: ${info.colorSpace}", info.colorSpace?.contains("sRGB") == true)
    }

    @Test fun infoButtonShowsTheFileNameOverThePhoto() {
        val uri = TestImages.write(context, "info-editor.png", TestImages.solid(640, 480, Color.BLUE))
        ActivityScenario.launch<PhotoEditActivity>(Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)).use { scenario ->
            scenario.awaitImageLoaded()
            compose.onNodeWithTag(EditorTags.INFO_CARD).assertDoesNotExist()
            compose.onNodeWithTag(EditorTags.INFO).performClick()
            // The card is tapped as a whole, so its texts are merged into it: the name in the unmerged tree
            compose.waitUntil(5_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasTestTag(EditorTags.INFO_NAME), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag(EditorTags.INFO_NAME, useUnmergedTree = true).assertTextEquals("info-editor.png")
            // Tap to close
            compose.onNodeWithTag(EditorTags.INFO_CARD).performClick()
            compose.onNodeWithTag(EditorTags.INFO_CARD).assertDoesNotExist()
        }
    }
}
