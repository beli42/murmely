package org.pixeldroid.media_editor.photoEdit.panel

/** A corner of the preview */
enum class Corner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT;

    /** The next corner clockwise */
    val next: Corner get() = entries[(ordinal + 1) % entries.size]
}

/**
 * Where a floating control goes in a container: in a corner, or where it was dragged to,
 * always fully inside. Pure, no Android; pixels.
 */
object FloatingPosition {

    /**
     * Top left of a control of [width] x [height] in [corner] of a container of
     * [containerWidth] x [containerHeight], [margin] from its edges; [bottomRightExtra] more
     * from the bottom in the bottom right corner (room for another button there)
     */
    fun inCorner(
        corner: Corner,
        containerWidth: Int, containerHeight: Int,
        width: Int, height: Int,
        margin: Int, bottomRightExtra: Int = 0,
    ): Pair<Int, Int> {
        val left = margin
        val right = containerWidth - width - margin
        val top = margin
        val bottom = containerHeight - height - margin
        val position = when (corner) {
            Corner.TOP_LEFT -> left to top
            Corner.TOP_RIGHT -> right to top
            Corner.BOTTOM_RIGHT -> right to bottom - bottomRightExtra
            Corner.BOTTOM_LEFT -> left to bottom
        }
        return clamp(position.first, position.second, containerWidth, containerHeight, width, height)
    }

    /** ([x], [y]) moved so that the control is fully inside the container */
    fun clamp(x: Int, y: Int, containerWidth: Int, containerHeight: Int, width: Int, height: Int): Pair<Int, Int> =
        x.coerceIn(0, maxOf(0, containerWidth - width)) to y.coerceIn(0, maxOf(0, containerHeight - height))
}
