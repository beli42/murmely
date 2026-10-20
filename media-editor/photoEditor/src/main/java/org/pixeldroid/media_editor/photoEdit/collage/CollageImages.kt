package org.pixeldroid.media_editor.photoEdit.collage

/** Which image is in which area of a collage (areas from 1). Pure, no Android. */
object CollageImages {

    /**
     * The areas from the top left clockwise, by where their middles are: around the collage's
     * middle, starting on the left. An area filling the middle (no corner) comes first.
     * [middleOf] gives an area's middle in parts of the collage (0..1).
     */
    fun clockwise(count: Int, middleOf: (Int) -> Pair<Float, Float>): List<Int> =
        (1..count).sortedWith(compareBy({ area ->
            val (x, y) = middleOf(area)
            val dx = x - 0.5f
            val dy = y - 0.5f
            if (dx * dx + dy * dy < 0.0001f) -1.0 else {
                // Angle on screen (y down: clockwise), from the left (9 o'clock): so the left of
                // two areas side by side comes first, and of four the top left one
                val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))
                ((angle + 180.0) % 360.0 + 360.0) % 360.0
            }
        }, { it }))

    /** Areas without an image, in [order] (all areas in the order they are filled) */
    fun <T> freeAreas(images: Map<Int, T>, count: Int, order: List<Int> = (1..count).toList()): List<Int> =
        order.filter { it !in images }

    /** How many images may be picked at once: the free areas, or all when none is free */
    fun <T> pickLimit(images: Map<Int, T>, count: Int): Int = freeAreas(images, count).size.takeIf { it > 0 } ?: count

    /**
     * [picked] images put into the free areas in [order]; when no area is free, into all areas
     * in that order (replacing). Images beyond the areas are left out.
     */
    fun <T> fill(images: Map<Int, T>, count: Int, picked: List<T>, order: List<Int> = (1..count).toList()): Map<Int, T> {
        val targets = freeAreas(images, count, order).ifEmpty { order }
        return images + targets.zip(picked)
    }

    /** The image of area [from] moved to [to]: swapped if [to] has one, else just moved there */
    fun <T> move(images: Map<Int, T>, from: Int, to: Int): Map<Int, T> {
        if (from == to) return images
        val moving = images[from] ?: return images
        val other = images[to]
        val rest = images - from - to
        return rest + (to to moving) + (other?.let { mapOf(from to it) } ?: emptyMap())
    }
}
