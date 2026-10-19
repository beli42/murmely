package org.pixeldroid.media_editor.photoEdit

/** How the editor screen is shared between the photo and the tools. Pure, no Android. */
object EditorLayout {

    /** Most of the height the photo may take (portrait), the rest is for the tools */
    const val PREVIEW_MAX = 0.7f

    /** Least: the tools never get more than half */
    const val PREVIEW_MIN = 0.5f

    /**
     * Height of the photo area in a portrait screen of [width] x [height]: as high as the image
     * (of [imageAspect] = height / width, with canvas and frame) needs at full width, within
     * [PREVIEW_MIN]..[PREVIEW_MAX] of the height. Unknown aspect (still loading): the most. Less if
     * the tools need more than the rest ([toolsMin]), but at least [PREVIEW_LEAST].
     */
    fun previewHeight(width: Float, height: Float, imageAspect: Float?, toolsMin: Float = 0f): Float {
        val most = height * PREVIEW_MAX
        val wanted = if (imageAspect == null || imageAspect <= 0f || width <= 0f) most
            else (width * imageAspect).coerceIn(height * PREVIEW_MIN, most)
        // The tools always get what they need (large system text), the photo never less than LEAST
        return wanted.coerceAtMost(height - toolsMin).coerceAtLeast(height * PREVIEW_LEAST)
    }

    /** The photo never gets less than this, whatever the tools need */
    const val PREVIEW_LEAST = 0.3f

    /**
     * Height (dp) the tools need at least at [fontScale] (the system text size): tabs, filter
     * groups, a filter tile with its name and smallest square, and the strength row
     */
    fun toolsMin(fontScale: Float): Float = 196f + 40f * fontScale
}
