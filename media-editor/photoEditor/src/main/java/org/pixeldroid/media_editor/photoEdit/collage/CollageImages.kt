package org.pixeldroid.media_editor.photoEdit.collage

/** Which image is in which area of a collage (areas from 1). Pure, no Android. */
object CollageImages {

    /** Areas without an image, in order */
    fun <T> freeAreas(images: Map<Int, T>, count: Int): List<Int> = (1..count).filter { it !in images }

    /** How many images may be picked at once: the free areas, or all when none is free */
    fun <T> pickLimit(images: Map<Int, T>, count: Int): Int = freeAreas(images, count).size.takeIf { it > 0 } ?: count

    /**
     * [picked] images put into the free areas in order; when no area is free, into the areas
     * from the first on (replacing). Images beyond the areas are left out.
     */
    fun <T> fill(images: Map<Int, T>, count: Int, picked: List<T>): Map<Int, T> {
        val free = freeAreas(images, count)
        val targets = free.ifEmpty { (1..count).toList() }
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
