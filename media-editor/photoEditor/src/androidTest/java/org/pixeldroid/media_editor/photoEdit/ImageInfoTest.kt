package org.pixeldroid.media_editor.photoEdit

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.info.ImageInfoReader
import java.io.File

/** Information about the opened photo */
@RunWith(AndroidJUnit4::class)
class ImageInfoTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun readsNameSizeFormatAndResolution() {
        val uri = TestImages.write(context, "info-test.png", TestImages.solid(640, 480, Color.RED))
        val info = ImageInfoReader.read(context, uri)
        assertEquals(File(context.cacheDir, "test-images/info-test.png").absolutePath, info.path)
        assertEquals("info-test.png", info.name)
        assertEquals("PNG", info.format)
        assertEquals(640, info.width)
        assertEquals(480, info.height)
        assertTrue("file size known: ${info.bytes}", (info.bytes ?: 0) > 0)
        assertTrue("colour space: ${info.colorSpace}", info.colorSpace?.contains("sRGB") == true)
    }

    @Test fun realPathOfAMediaStoreImage() {
        val folder = "${Environment.DIRECTORY_PICTURES}/BunnyTest-${System.currentTimeMillis()}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "info-store.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        try {
            resolver.openOutputStream(uri)!!.use { TestImages.solid(64, 48, Color.GREEN).compress(Bitmap.CompressFormat.PNG, 100, it) }
            val info = ImageInfoReader.read(context, uri)
            val path = info.path
            assertNotNull("path known", path)
            assertTrue("full path: $path", path!!.startsWith("/") && path.endsWith("$folder/info-store.png"))
            assertEquals("info-store.png", info.name)
        } finally {
            resolver.delete(uri, null, null)
        }
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
            // Above it, where the file is
            compose.onNodeWithTag(EditorTags.INFO_LOCATION, useUnmergedTree = true).assertTextEquals(File(context.cacheDir, "test-images").absolutePath)
            // Tap to close
            compose.onNodeWithTag(EditorTags.INFO_CARD).performClick()
            compose.onNodeWithTag(EditorTags.INFO_CARD).assertDoesNotExist()
        }
    }
}
