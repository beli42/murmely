package org.pixeldroid.media_editor.photoEdit

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.overlay.Overlay
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.overlay.StickerCatalog
import org.pixeldroid.media_editor.photoEdit.overlay.StickerItem
import org.pixeldroid.media_editor.photoEdit.panel.PanelTags

/** Built-in stickers, and stickers moved, scaled and turned */
@RunWith(AndroidJUnit4::class)
class StickerTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun everyBuiltInStickerIsThere() = runBlocking {
        assertEquals("no sticker twice", StickerCatalog.all.size, StickerCatalog.all.toSet().size)
        for (code in StickerCatalog.all) {
            val bitmap = context.assets.open(StickerCatalog.assetPath(code)).use { BitmapFactory.decodeStream(it) }
            assertNotNull("sticker $code", bitmap)
            assertEquals("sticker $code width", 512, bitmap.width)
            assertEquals("sticker $code height", 512, bitmap.height)
        }
        // Loaded like any other sticker (by uri), as for the preview and the saved image
        assertNotNull(getBitmap(context, StickerCatalog.uri(StickerCatalog.all.first()).toUri(), 64, 64, fit = true))
    }

    /** Scaled and turned 45°: a diamond instead of a square */
    @Test fun turnedStickerIsDrawnTurned() = runBlocking {
        val blue = TestImages.write(context, "sticker-square.png", TestImages.solid(100, 100, Color.BLUE))
        val overlay = Overlay(stickers = listOf(StickerItem(blue.toString(), 0.5f, 0.5f, id = 1)))
            .transformSticker(1, 0.5f, 0.5f, 0.4f, 45f)
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        OverlayRenderer.draw(Canvas(bitmap), 1000, 1000, overlay, OverlayRenderer.loadStickers(context, overlay, 1000))
        // 400 px square turned around (500, 500): its corners point up, down, left and right
        ImageIntegrity.assertColor(bitmap, 500, 500, Color.BLUE, 8, "centre")
        ImageIntegrity.assertColor(bitmap, 500, 240, Color.BLUE, 8, "upper corner (above the unturned square)")
        ImageIntegrity.assertColor(bitmap, 320, 320, Color.WHITE, 8, "where the unturned corner was")
    }

    @Test fun addingSelectsAndEveryGestureIsOneStep() {
        lateinit var model: PhotoEditViewModel
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.toggleTool(PhotoEditViewModel.ShownView.Sticker)
            model.addSticker(StickerCatalog.uri("1f430"))
        }
        val added = model.overlay.value.stickers.single()
        assertEquals("added in the middle", 0.5f to 0.5f, added.x to added.y)
        assertEquals("selected", added.id, model.selectedSticker.value)

        instrumentation.runOnMainSync { model.transformSticker(added.copy(x = 0.3f, width = 0.6f, rotation = -30f)) }
        assertEquals(added.copy(x = 0.3f, width = 0.6f, rotation = -30f), model.overlay.value.stickers.single())
        assertEquals(2, model.changes.size)

        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals(added, model.overlay.value.stickers.single())

        // Dropped on the bin: removed, deselected; undo brings it back
        instrumentation.runOnMainSync { model.removeSticker(added.id) }
        assertEquals(0, model.overlay.value.stickers.size)
        assertNull(model.selectedSticker.value)
        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals(added, model.overlay.value.stickers.single())

        // Leaving the sticker tool deselects
        instrumentation.runOnMainSync { model.toggleTool(PhotoEditViewModel.ShownView.Sticker) }
        assertNull(model.selectedSticker.value)
    }

    /** In the editor: sticker tool, category, tap a sticker: it is on the photo, selected */
    @Test fun pickerAddsTheSticker() {
        val uri = TestImages.write(context, "sticker-source.png", TestImages.gradient(800, 600))
        ActivityScenario.launch<PhotoEditActivity>(
            Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)
        ).use { scenario ->
            lateinit var model: PhotoEditViewModel
            scenario.onActivity { model = ViewModelProvider(it, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java] }
            compose.onNodeWithTag(EditorTags.tab(EditorTab.EDIT)).performClick()
            compose.onNodeWithTag(PanelTags.STICKERS).performClick()
            // The tools area is small: scroll to them first
            compose.onNodeWithTag(PanelTags.stickerCategory(StickerCatalog.Category.ANIMALS)).performScrollTo().performClick()
            compose.onNodeWithTag(PanelTags.sticker("1f430")).performScrollTo().performClick()
            compose.waitForIdle()
            val sticker = model.overlay.value.stickers.single()
            assertEquals(StickerCatalog.uri("1f430"), sticker.uri)
            assertEquals(sticker.id, model.selectedSticker.value)
        }
    }
}
