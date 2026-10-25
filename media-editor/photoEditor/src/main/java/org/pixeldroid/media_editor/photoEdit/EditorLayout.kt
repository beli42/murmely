package org.pixeldroid.media_editor.photoEdit

/** How the editor screen is shared between the photo and the tools. Pure, no Android. */
object EditorLayout {

    /** Most of the height the photo may take (portrait), the rest is for the tools */
    const val PREVIEW_MAX = 0.7f

    /** The photo always gets at least this much of the height */
    const val PREVIEW_MIN = 0.55f

    /**
     * Share of the width for the photo in landscape (the tools beside it), in percent: set in
     * the app's settings, from [LANDSCAPE_PHOTO_MIN] to [LANDSCAPE_PHOTO_MAX] (shared with the
     * video editor, see the common module)
     */
    const val LANDSCAPE_PHOTO_DEFAULT = org.pixeldroid.media_editor.common.LANDSCAPE_PHOTO_DEFAULT
    const val LANDSCAPE_PHOTO_MIN = org.pixeldroid.media_editor.common.LANDSCAPE_PHOTO_MIN
    const val LANDSCAPE_PHOTO_MAX = org.pixeldroid.media_editor.common.LANDSCAPE_PHOTO_MAX

    /** [percent] as a share (0..1), limited to the allowed range */
    fun landscapePhotoShare(percent: Int): Float = org.pixeldroid.media_editor.common.landscapePhotoShare(percent)

    /**
     * Width of the photo area in a landscape screen of [width] x [height]: as wide as the image
     * (of [imageAspect] = height / width) is at full height, at most [share] of the width, so the
     * photo sits on the left without empty room beside it. Unknown aspect: [share] of the width.
     */
    fun landscapePhotoWidth(width: Float, height: Float, imageAspect: Float?, share: Float): Float {
        val most = width * share
        if (imageAspect == null || imageAspect <= 0f) return most
        return (height / imageAspect).coerceAtMost(most)
    }

    /** About this many filter tiles side by side; the rest scroll sideways */
    const val TILES_ACROSS = 2.6f

    /** Size of a filter tile's square the layout aims for: from [TILE_MIN] to [TILE_MAX] dp */
    const val TILE_MIN = 72f
    const val TILE_MAX = 160f

    /** The square of a filter tile the layout aims for on a screen [width] dp wide */
    fun tileTarget(width: Float): Float = (width / TILES_ACROSS).coerceIn(TILE_MIN, TILE_MAX)

    /**
     * Height (dp) of the tools without the tile's square, at [fontScale] (the system text size):
     * tabs, filter groups, the tile's name and padding, and the strength row
     */
    fun toolsWithoutTile(fontScale: Float): Float = 156f + 40f * fontScale

    /**
     * Height of the photo area in a portrait screen of [width] x [height] dp: as high as the
     * image (of [imageAspect] = height / width, with canvas and frame) needs at full width, at
     * most [PREVIEW_MAX]; less if the tools need the room for filter tiles of [tileTarget] at
     * [fontScale]; never less than [PREVIEW_MIN]. Unknown aspect (still loading): as if tall.
     */
    fun previewHeight(width: Float, height: Float, imageAspect: Float?, fontScale: Float = 1f): Float {
        val wanted = if (imageAspect == null || imageAspect <= 0f || width <= 0f) height * PREVIEW_MAX
            else (width * imageAspect).coerceAtMost(height * PREVIEW_MAX)
        val leftForPhoto = height - toolsWithoutTile(fontScale) - tileTarget(width)
        return wanted.coerceAtMost(leftForPhoto).coerceAtLeast(height * PREVIEW_MIN)
    }
}
