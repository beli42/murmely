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
     * [PREVIEW_MIN]..[PREVIEW_MAX] of the height. Unknown aspect (still loading): the most.
     */
    fun previewHeight(width: Float, height: Float, imageAspect: Float?): Float {
        val most = height * PREVIEW_MAX
        if (imageAspect == null || imageAspect <= 0f || width <= 0f) return most
        return (width * imageAspect).coerceIn(height * PREVIEW_MIN, most)
    }
}
