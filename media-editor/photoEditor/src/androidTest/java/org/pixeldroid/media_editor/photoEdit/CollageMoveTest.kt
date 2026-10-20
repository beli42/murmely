package org.pixeldroid.media_editor.photoEdit

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.collage.CollageTags
import org.pixeldroid.media_editor.photoEdit.collage.ImagesContent

/** Collage: choosing several photos at once, and swapping them by dragging */
@RunWith(AndroidJUnit4::class)
class CollageMoveTest {

    @get:Rule val compose = createComposeRule()

    private var moved: Pair<Int, Int>? = null
    private var pickedMany = false

    /** Layout 2: two areas side by side; [filled] have an image */
    private fun collage(filled: Set<Int>) = compose.setContent {
        BunnyTheme {
            ImagesContent(
                layout = 2, preview = null, canSave = false, saving = false, showSaveAs = false,
                snackbarHostState = SnackbarHostState(),
                onAreaClick = {}, filledAreas = filled,
                onPickMany = { pickedMany = true }, onMove = { from, to -> moved = from to to },
                onCycleMode = {}, onSave = {}, onSaveAs = {}, onBack = {},
            )
        }
    }

    /** Long press on area 1, then drag to the middle of [to] */
    private fun dragFirstTo(to: Int) {
        val from = compose.onNodeWithTag(CollageTags.area(1)).getBoundsInRoot()
        val target = compose.onNodeWithTag(CollageTags.area(to)).getBoundsInRoot()
        val dx = (target.left + target.right) / 2 - (from.left + from.right) / 2
        val dy = (target.top + target.bottom) / 2 - (from.top + from.bottom) / 2
        compose.onNodeWithTag(CollageTags.area(1)).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            val steps = 10
            repeat(steps) { moveBy(Offset(dx.toPx() / steps, dy.toPx() / steps)) }
            up()
        }
        compose.waitForIdle()
    }

    @Test fun draggingOneImageOntoAnotherSwapsThem() {
        collage(setOf(1, 2))
        dragFirstTo(2)
        assertEquals(1 to 2, moved)
    }

    @Test fun anEmptyAreaCannotBeDragged() {
        collage(emptySet())
        dragFirstTo(2)
        assertNull(moved)
    }

    @Test fun choosingSeveralPhotos() {
        collage(emptySet())
        compose.onNodeWithTag(CollageTags.PICK_MANY).performClick()
        assertTrue(pickedMany)
    }
}
