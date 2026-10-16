package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.overlay.Overlay
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.overlay.TextFont
import org.pixeldroid.media_editor.photoEdit.overlay.TextItem
import org.pixeldroid.media_editor.photoEdit.overlay.TextTypefaces
import org.pixeldroid.media_editor.photoEdit.overlay.ToolStyle
import org.pixeldroid.media_editor.photoEdit.panel.PanelTags
import org.pixeldroid.media_editor.photoEdit.panel.ToolSettingsPanel

/** The bundled fonts: loaded, drawn, chosen */
@RunWith(AndroidJUnit4::class)
class FontTest {

    @get:Rule val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before fun loadFonts() = TextTypefaces.init(context)

    /** Every bundled font is there and looks different from the default font */
    @Test fun everyFontLoads() {
        val defaultWidth = Paint().apply { textSize = 100f }.measureText(SAMPLE)
        for (font in TextFont.entries.filter { it != TextFont.DEFAULT }) {
            val typeface = TextTypefaces.typeface(font)
            assertNotSame("$font loaded", Typeface.DEFAULT, typeface)
            val width = Paint().apply { textSize = 100f; this.typeface = typeface }.measureText(SAMPLE)
            assertNotEquals("$font has its own letter widths", defaultWidth, width, 1f)
        }
    }

    /** The saved image uses the font of the text */
    @Test fun textIsDrawnInItsFont() {
        fun draw(font: TextFont) = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            OverlayRenderer.draw(Canvas(this), 800, 400, Overlay(texts = listOf(TextItem(SAMPLE, 0.5f, 0.5f, font = font))))
        }
        val plain = draw(TextFont.DEFAULT)
        val lobster = draw(TextFont.LOBSTER)
        var different = 0
        for (y in 0 until 400 step 2) for (x in 0 until 800 step 2) if (plain.getPixel(x, y) != lobster.getPixel(x, y)) different++
        assertTrue("$different pixels differ", different > 500)
    }

    /** Chosen in the settings, used for the next new text */
    @Test fun chosenFontIsUsedForNewTexts() {
        var style by mutableStateOf(ToolStyle())
        compose.setContent { BunnyTheme { ToolSettingsPanel(style, forText = true, onChange = { style = it }) } }
        compose.onNodeWithTag(PanelTags.font(TextFont.PACIFICO)).performScrollTo().performClick()
        assertEquals(TextFont.PACIFICO, style.font)

        lateinit var model: PhotoEditViewModel
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.onToolStyleChange(style)
            model.startNewText(0.5f, 0.5f)
            model.updateTextEdit("Hallo")
            model.finishTextEdit()
        }
        assertEquals(TextFont.PACIFICO, model.overlay.value.texts.single().font)
    }

    private companion object {
        const val SAMPLE = "Grüße aus Zürich"
    }
}
