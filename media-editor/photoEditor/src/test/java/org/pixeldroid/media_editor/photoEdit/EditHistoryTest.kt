package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    private val abc = EditHistory<String>().push("a").push("b").push("c")

    @Test fun pushKeepsOrder() = assertEquals(listOf("a", "b", "c"), abc.changes)

    @Test fun undoAndRedo() {
        val undone = abc.undo().undo()
        assertEquals(listOf("a"), undone.changes)
        val (first, afterFirst) = undone.redo()!!
        assertEquals("b", first)
        val (second, afterSecond) = afterFirst.redo()!!
        assertEquals("c", second)
        assertEquals(abc.changes, afterSecond.changes)
        assertFalse(afterSecond.canRedo)
    }

    @Test fun newEditDropsRedo() {
        val history = abc.undo().push("d")
        assertEquals(listOf("a", "b", "d"), history.changes)
        assertNull(history.redo())
    }

    @Test fun undoOnEmptyDoesNothing() {
        val empty = EditHistory<String>()
        assertEquals(empty, empty.undo())
        assertFalse(empty.canUndo)
    }

    @Test fun undoAllCanBeRedoneInOriginalOrder() {
        var history = abc.undoAll()
        assertTrue(history.changes.isEmpty())
        val redone = mutableListOf<String>()
        while (true) {
            val (change, next) = history.redo() ?: break
            redone += change
            history = next
        }
        assertEquals(listOf("a", "b", "c"), redone)
    }

    @Test fun historyIsNotChangedByOperations() {
        abc.undo(); abc.push("x"); abc.undoAll()
        assertEquals("immutable", listOf("a", "b", "c"), abc.changes)
    }
}
