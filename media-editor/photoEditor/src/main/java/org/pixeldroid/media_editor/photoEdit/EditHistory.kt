package org.pixeldroid.media_editor.photoEdit

/**
 * Undo/redo history of edits. Immutable: every operation returns a new history.
 * Pure, no Android dependencies.
 *
 * @param changes edits in the order they were made
 * @param redoable undone edits; the last one is redone first
 */
data class EditHistory<T>(
    val changes: List<T> = emptyList(),
    val redoable: List<T> = emptyList(),
) {
    val canUndo get() = changes.isNotEmpty()
    val canRedo get() = redoable.isNotEmpty()

    /** A new edit; it makes the undone edits unreachable */
    fun push(change: T) = EditHistory(changes + change, emptyList())

    fun undo(): EditHistory<T> =
        if (!canUndo) this else EditHistory(changes.dropLast(1), redoable + changes.last())

    /** The edit to redo and the history after redoing it, or null if there is nothing to redo */
    fun redo(): Pair<T, EditHistory<T>>? =
        redoable.lastOrNull()?.let { it to EditHistory(changes + it, redoable.dropLast(1)) }

    /** Everything undone at once ("reset"), but still redoable in the original order */
    fun undoAll() = EditHistory(emptyList(), redoable + changes.reversed())
}
