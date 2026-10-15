package org.pixeldroid.media_editor.photoEdit

import android.content.Intent
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.overlay.TextGeometry
import org.pixeldroid.media_editor.photoEdit.panel.PanelTags

/** Texts typed on the photo, moved, scaled, turned and removed */
@RunWith(AndroidJUnit4::class)
class TextTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun typingAddsChangesAndRemovesTexts() {
        lateinit var model: PhotoEditViewModel
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.toggleTool(PhotoEditViewModel.ShownView.Text)
            // Nothing typed: nothing added
            model.startNewText(0.3f, 0.4f)
            model.finishTextEdit()
        }
        assertEquals(0, model.changes.size)

        instrumentation.runOnMainSync {
            model.startNewText(0.3f, 0.4f)
            model.updateTextEdit("  Hallo ")
            model.finishTextEdit()
        }
        val added = model.overlay.value.texts.single()
        assertEquals("Hallo", added.text)
        assertEquals("centred where tapped", 0.3f to 0.4f, added.x to added.y)
        assertEquals("selected", added.id, model.selectedItem.value)
        assertNull(model.editingText.value)

        // Changed in place; unchanged is no step
        instrumentation.runOnMainSync {
            model.startEditText(added.id)
            model.finishTextEdit()
            model.startEditText(added.id)
            model.updateTextEdit("Welt")
            model.finishTextEdit()
        }
        assertEquals("Welt", model.overlay.value.texts.single().text)
        assertEquals(2, model.changes.size)

        // Emptied: removed
        instrumentation.runOnMainSync {
            model.startEditText(added.id)
            model.updateTextEdit("")
            model.finishTextEdit()
        }
        assertEquals(0, model.overlay.value.texts.size)
        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals("Welt", model.overlay.value.texts.single().text)
    }

    @Test fun textsAreTransformedAndRemovedInOneStepEach() {
        lateinit var model: PhotoEditViewModel
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.addText("Hi", 0.5f, 0.5f)
        }
        val text = model.overlay.value.texts.single()
        instrumentation.runOnMainSync { model.transformText(text.copy(x = 0.2f, size = 0.25f, rotation = 45f)) }
        assertEquals(text.copy(x = 0.2f, size = 0.25f, rotation = 45f), model.overlay.value.texts.single())

        instrumentation.runOnMainSync { model.selectItem(text.id); model.removeText(text.id) }
        assertEquals(0, model.overlay.value.texts.size)
        assertNull("deselected", model.selectedItem.value)
        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals(text.copy(x = 0.2f, size = 0.25f, rotation = 45f), model.overlay.value.texts.single())
        assertEquals("add, transform", 2, model.changes.size)
    }

    /** Typed at the border, or made longer: moved in, so the saved image doesn't cut it off */
    @Test fun textsStayOnTheImage() {
        lateinit var model: PhotoEditViewModel
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.imageAspect = 0.75f
            model.startNewText(0.99f, 0.99f)
            model.updateTextEdit("Hallo Welt")
            model.finishTextEdit()
        }
        val text = model.overlay.value.texts.single()
        val width = OverlayRenderer.textWidth(text)
        assertTrue("right edge on the image", text.x + width / 2 <= 1f + 1e-4f)
        assertTrue("bottom edge on the image", (text.y * 0.75f) + text.size * TextGeometry.HEIGHT / 2 <= 0.75f + 1e-4f)

        // Made much longer: moved in again (and made smaller if it can't fit), in one step
        instrumentation.runOnMainSync {
            model.startEditText(text.id)
            model.updateTextEdit("Hallo Welt, das ist ein sehr langer Text")
            model.finishTextEdit()
        }
        val longer = model.overlay.value.texts.single()
        val longerWidth = OverlayRenderer.textWidth(longer)
        assertTrue("left edge on the image", longer.x - longerWidth / 2 >= -1e-4f)
        assertTrue("right edge on the image", longer.x + longerWidth / 2 <= 1f + 1e-4f)
        assertEquals("add, change", 2, model.changes.size)
        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals(text, model.overlay.value.texts.single())
    }

    /** Undo while typing drops what was typed instead of adding and undoing it */
    @Test fun undoWhileTypingDropsIt() {
        lateinit var model: PhotoEditViewModel
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.addText("A", 0.5f, 0.5f)
            model.startNewText(0.2f, 0.2f)
            model.updateTextEdit("B")
            model.undoChange()
        }
        assertNull(model.editingText.value)
        assertEquals(0, model.overlay.value.texts.size)
    }

    /** In the editor: the field on the photo, typing, Done */
    @Test fun typingOnThePhoto() {
        val uri = TestImages.write(context, "text-source.png", TestImages.gradient(800, 600))
        ActivityScenario.launch<PhotoEditActivity>(
            Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)
        ).use { scenario ->
            lateinit var model: PhotoEditViewModel
            scenario.onActivity { model = ViewModelProvider(it, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java] }
            scenario.awaitImageLoaded()
            compose.onNodeWithTag(EditorTags.tab(EditorTab.EDIT)).performClick()
            compose.onNodeWithTag(PanelTags.TEXT).performClick()
            // What a tap on empty space does
            scenario.onActivity { model.startNewText(0.5f, 0.5f) }
            compose.onNodeWithTag(EditorTags.TEXT_FIELD).performTextInput("Hallo")
            compose.onNodeWithTag(EditorTags.TEXT_FIELD).performImeAction()
            compose.waitForIdle()
            assertEquals("Hallo", model.overlay.value.texts.single().text)
            compose.onNodeWithTag(EditorTags.TEXT_FIELD).assertDoesNotExist()

            // Changing it: the field shows the text, emptying it removes the text
            val id = model.overlay.value.texts.single().id
            scenario.onActivity { model.startEditText(id) }
            compose.onNodeWithTag(EditorTags.TEXT_FIELD).assertTextEquals("Hallo")
            compose.onNodeWithTag(EditorTags.TEXT_FIELD).performTextClearance()
            compose.onNodeWithTag(EditorTags.TEXT_FIELD).performImeAction()
            compose.waitForIdle()
            assertEquals(0, model.overlay.value.texts.size)
        }
    }
}
