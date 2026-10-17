package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.overlay.Overlay
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.overlay.TextEffects
import org.pixeldroid.media_editor.photoEdit.overlay.TextItem
import org.pixeldroid.media_editor.photoEdit.overlay.ToolStyle
import org.pixeldroid.media_editor.photoEdit.panel.PanelTags
import org.pixeldroid.media_editor.photoEdit.panel.ToolSettingsPanel

/** Outline, letter spacing and shadows of texts */
@RunWith(AndroidJUnit4::class)
class TextEffectsTest {

    @get:Rule val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private val blue = 0xFF0000FF.toInt()
    private val yellow = 0xFFFDD835.toInt()

    /** A red block text in the middle of a white 1000 x 600 image */
    private fun render(effects: TextEffects): Bitmap {
        val bitmap = Bitmap.createBitmap(1000, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val text = TextItem("\\u2588\\u2588", 0.5f, 0.5f, size = 0.2f, color = Color.RED, effects = effects)
        OverlayRenderer.draw(Canvas(bitmap), 1000, 600, Overlay(texts = listOf(text)))
        return bitmap
    }

    private fun pixels(bitmap: Bitmap, matches: (Int) -> Boolean): List<Pair<Int, Int>> =
        (0 until bitmap.height step 2).flatMap { y -> (0 until bitmap.width step 2).filter { matches(bitmap.getPixel(it, y)) }.map { it to y } }

    private fun close(a: Int, b: Int) =
        kotlin.math.abs(Color.red(a) - Color.red(b)) < 30 && kotlin.math.abs(Color.green(a) - Color.green(b)) < 30 &&
            kotlin.math.abs(Color.blue(a) - Color.blue(b)) < 30

    @Test fun outlineHasItsColourAroundTheLetters() {
        assertTrue("no outline: no blue", pixels(render(TextEffects())) { close(it, blue) }.isEmpty())
        val outlined = render(TextEffects(outline = 0.1f, outlineColor = blue))
        assertTrue("outline drawn", pixels(outlined) { close(it, blue) }.size > 50)
        assertTrue("letters stay red inside", pixels(outlined) { close(it, Color.RED) }.size > 500)
    }

    @Test fun dropShadowLiesOffsetInItsColour() {
        val image = render(TextEffects(dropShadow = 0.2f, dropShadowColor = yellow, dropShadowAngle = 45f))
        val shadow = pixels(image) { close(it, yellow) }
        val letters = pixels(image) { close(it, Color.RED) }
        assertTrue("drop shadow drawn", shadow.size > 50)
        // Right and below the letters
        val sx = shadow.map { it.first }.average()
        val sy = shadow.map { it.second }.average()
        val lx = letters.map { it.first }.average()
        val ly = letters.map { it.second }.average()
        fun bounds(points: List<Pair<Int, Int>>) =
            "x ${points.minOf { it.first }}..${points.maxOf { it.first }}, y ${points.minOf { it.second }}..${points.maxOf { it.second }}"
        // Kept for a look (see the command in the chat)
        runCatching {
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "drop-shadow-test.png")
                .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val where = "shadow at (%.0f, %.0f) from %d pixels [%s], letters at (%.0f, %.0f) from %d [%s]"
            .format(sx, sy, shadow.size, bounds(shadow), lx, ly, letters.size, bounds(letters))
        assertTrue("right of the letters: $where", sx > lx)
        assertTrue("below the letters: $where", sy > ly)
    }

    @Test fun softShadowDarkensAroundTheLetters() {
        val grey = { p: Int -> Color.red(p) in 60..230 && kotlin.math.abs(Color.red(p) - Color.green(p)) < 12 }
        assertTrue(pixels(render(TextEffects())) { grey(it) }.size < 10)
        assertTrue("soft grey shadow", pixels(render(TextEffects(shadow = 1f))) { grey(it) }.size > 100)
    }

    @Test fun letterSpacingMakesTheTextWider() {
        val plain = TextItem("Hallo", 0.5f, 0.5f, size = 0.1f)
        val spaced = plain.copy(effects = TextEffects(letterSpacing = 0.5f))
        assertTrue(OverlayRenderer.textWidth(spaced) > OverlayRenderer.textWidth(plain) * 1.3f)
    }

    @Test fun effectsGoToTheSelectedTextAndCanBeUndone() {
        lateinit var model: PhotoEditViewModel
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.toggleTool(PhotoEditViewModel.ShownView.Text)
            model.addText("Hi", 0.5f, 0.5f)
        }
        val text = model.overlay.value.texts.single()
        instrumentation.runOnMainSync {
            model.selectItem(text.id)
            model.onToolStyleChange(model.toolStyle.value.copy(effects = TextEffects(outline = 0.1f)))
        }
        assertEquals(0.1f, model.overlay.value.texts.single().effects.outline, 0f)
        val end = System.currentTimeMillis() + 5_000
        while (model.changes.size < 2 && System.currentTimeMillis() < end) Thread.sleep(50)
        assertEquals("add, effects", 2, model.changes.size)
        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals(TextEffects(), model.overlay.value.texts.single().effects)
    }

    @Test fun effectsAreFoldedAwayUntilOpened() {
        var style by mutableStateOf(ToolStyle())
        compose.setContent { BunnyTheme { ToolSettingsPanel(style, forText = true, onChange = { style = it }) } }
        compose.onNodeWithTag(PanelTags.OUTLINE).assertDoesNotExist()
        compose.onNodeWithTag(PanelTags.EFFECTS).performClick()
        compose.onNodeWithTag(PanelTags.OUTLINE).assertExists()
        compose.onNodeWithTag(PanelTags.DROP_SHADOW).assertExists()
        // Colours only once there is an outline
        compose.onNodeWithTag(PanelTags.effectColor(PanelTags.OUTLINE, 0xFFFFFFFF.toInt())).assertDoesNotExist()
        style = style.copy(effects = TextEffects(outline = 0.1f))
        compose.onNodeWithTag(PanelTags.effectColor(PanelTags.OUTLINE, 0xFFFFFFFF.toInt())).performClick()
        assertEquals(0xFFFFFFFF.toInt(), style.effects.outlineColor)
    }
}
