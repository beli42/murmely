package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.overlay.Overlay
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.overlay.StickerItem
import org.pixeldroid.media_editor.photoEdit.overlay.TextEffects
import org.pixeldroid.media_editor.photoEdit.overlay.TextItem
import org.pixeldroid.media_editor.photoEdit.panel.TemplatePanel
import org.pixeldroid.media_editor.photoEdit.panel.TemplateTags
import org.pixeldroid.media_editor.photoEdit.templates.TemplateImages
import org.pixeldroid.media_editor.photoEdit.templates.TemplateJson
import java.io.File
import kotlin.math.abs

/** Watermarks: opacity of stickers and texts, and templates that bring them along */
@RunWith(AndroidJUnit4::class)
class WatermarkTest {

    @get:Rule val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val builtIn = BuiltInFilters()

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun near(actual: Int, r: Int, g: Int, b: Int) =
        abs(Color.red(actual) - r) < 16 && abs(Color.green(actual) - g) < 16 && abs(Color.blue(actual) - b) < 16

    @Test fun halfOpaqueStickerMixesWithThePhoto() {
        val image = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val red = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val sticker = StickerItem("x", 0.5f, 0.5f, width = 0.5f, id = 1, opacity = 0.5f)
        OverlayRenderer.draw(Canvas(image), 200, 200, Overlay(stickers = listOf(sticker)), mapOf(1L to red))
        val mid = image.getPixel(100, 100)
        assertTrue("red over white at 50 %: ${Integer.toHexString(mid)}", near(mid, 255, 128, 128))
    }

    @Test fun halfOpaqueTextIsSeeThroughAsAWhole() {
        val image = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        // Block letters with a blue outline: inside them only the red letters, half see-through
        val text = TextItem("\u2588", 0.5f, 0.5f, size = 0.3f, color = Color.RED, opacity = 0.5f,
            effects = TextEffects(outline = 0.05f, outlineColor = Color.BLUE))
        OverlayRenderer.draw(Canvas(image), 600, 300, Overlay(texts = listOf(text)))
        val mid = image.getPixel(300, 150)
        assertTrue("letters: red over white at 50 %, no outline showing through: ${Integer.toHexString(mid)}", near(mid, 255, 128, 128))
    }

    @Test fun stickerOpacityIsOneStepAndCanBeUndone() {
        lateinit var model: PhotoEditViewModel
        onMain {
            model = PhotoEditViewModel()
            model.toggleTool(PhotoEditViewModel.ShownView.Sticker)
            model.addSticker("file:///android_asset/stickers/noto/1f430.webp")
            model.selectItem(model.overlay.value.stickers.single().id)
            model.onStickerOpacityChange(0.6f)
            model.onStickerOpacityChange(0.4f)
        }
        assertEquals("shown right away", 0.4f, model.overlay.value.stickers.single().opacity, 0f)
        val end = System.currentTimeMillis() + 5_000
        while (model.changes.size < 2 && System.currentTimeMillis() < end) Thread.sleep(50)
        assertEquals("add, opacity", 2, model.changes.size)
        onMain { model.undoChange() }
        assertEquals(1f, model.overlay.value.stickers.single().opacity, 0f)
    }

    /** Saved with a watermark, applied to another photo: in the same corner, size and opacity, on top */
    @Test fun templateBringsTheWatermarkToAnotherPhoto() {
        lateinit var json: String
        onMain {
            val first = PhotoEditViewModel()
            first.addSticker("file:///android_asset/stickers/noto/1f430.webp", x = 0.9f, y = 0.9f)
            first.selectItem(first.overlay.value.stickers.single().id)
            first.onStickerOpacityChange(0.4f)
            first.addText("© beli42", 0.2f, 0.95f)
            json = TemplateJson.write(first.currentTemplate("Mit Zeichen", builtIn, withOverlay = true))
        }
        val template = TemplateJson.read("Mit Zeichen", json)
        assertEquals(1, template.stickers.size)
        assertEquals(0.4f, template.stickers.single().opacity, 0f)
        assertEquals("© beli42", template.texts.single().text)

        lateinit var second: PhotoEditViewModel
        onMain {
            second = PhotoEditViewModel()
            second.addText("Hi", 0.5f, 0.5f)
        }
        val own = second.overlay.value.texts.single()
        onMain { second.applyTemplate(template, builtIn) }
        val overlay = second.overlay.value
        assertEquals("own text and the template's", 2, overlay.texts.size)
        val mark = overlay.stickers.single()
        assertEquals(0.9f, mark.x, 1e-6f)
        assertEquals(0.9f, mark.y, 1e-6f)
        assertEquals(0.4f, mark.opacity, 0f)
        assertNotEquals("own id", own.id, mark.id)
        assertTrue("in front of the photo's own text", mark.layer > own.layer)

        onMain { second.undoChange() }
        assertEquals("undo removes them again", listOf(own), second.overlay.value.texts)
        assertTrue(second.overlay.value.stickers.isEmpty())
    }

    /** A gallery image is copied: the template still has it when the original is gone */
    @Test fun keptImageOutlivesTheOriginal() {
        val original = File(context.cacheDir, "watermark-original.png")
        Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            .let { b -> original.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val kept = TemplateImages.keep(context, Uri.fromFile(original).toString())
        assertNotEquals(Uri.fromFile(original).toString(), kept)
        original.delete()
        val copy = File(Uri.parse(kept).path!!)
        assertTrue("copy still there", copy.exists())
        // Built-in stickers need no copy
        assertEquals("file:///android_asset/stickers/noto/1f430.webp", TemplateImages.keep(context, "file:///android_asset/stickers/noto/1f430.webp"))
        // Deleting the template removes its copy
        val template = org.pixeldroid.media_editor.photoEdit.templates.Template("x", stickers = listOf(StickerItem(kept, 0.5f, 0.5f)))
        TemplateImages.forget(context, template, emptyList())
        assertTrue("copy deleted with the template", !copy.exists())
    }

    @Test fun savingAsksWhetherToKeepStickersAndTexts() {
        var saved: Pair<String, Boolean>? = null
        compose.setContent {
            BunnyTheme { TemplatePanel(templates = emptyList(), vertical = false, onSave = { n, w -> saved = n to w }, onApply = {}, onRename = { _, _ -> }, onDelete = {}) }
        }
        compose.onNodeWithTag(TemplateTags.SAVE).performClick()
        compose.onNodeWithTag(TemplateTags.NAME).performTextReplacement("Wasserzeichen")
        compose.onNodeWithTag(TemplateTags.WITH_OVERLAY).performClick()
        compose.onNodeWithTag(TemplateTags.OK).performClick()
        assertEquals("Wasserzeichen" to true, saved)
    }
}
