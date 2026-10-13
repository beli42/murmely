package org.pixeldroid.media_editor.photoEdit.overlay

import kotlin.math.roundToInt

/**
 * Where an image of [imageWidth] x [imageHeight] is shown inside a view of [viewWidth] x
 * [viewHeight] when it is scaled to fit (bars on two sides), and conversion between view pixels
 * and image-relative coordinates. Pure, no Android dependencies.
 */
data class ImageFrame(val imageWidth: Int, val imageHeight: Int, val viewWidth: Int, val viewHeight: Int) {

    /** Size of the image on screen */
    val shownWidth: Int
    val shownHeight: Int

    init {
        val imageRatio = imageWidth.toDouble() / imageHeight
        val viewRatio = viewWidth.toDouble() / viewHeight
        if (imageRatio > viewRatio) {
            // Wider than the view: full width, bars on top and bottom
            shownWidth = viewWidth
            shownHeight = (viewWidth / imageRatio).roundToInt()
        } else {
            // Taller: full height, bars on the sides
            shownWidth = (viewHeight * imageRatio).roundToInt()
            shownHeight = viewHeight
        }
    }

    /** Width of each bar left and right */
    val sideBar: Int get() = (viewWidth - shownWidth) / 2
    /** Height of each bar above and below */
    val topBar: Int get() = (viewHeight - shownHeight) / 2

    /** A point in view pixels as image-relative coordinates (may be outside 0..1 on a bar) */
    fun toImage(viewX: Float, viewY: Float) =
        RelativePoint((viewX - sideBar) / shownWidth, (viewY - topBar) / shownHeight)

    /** An image-relative point in view pixels */
    fun toView(point: RelativePoint): Pair<Float, Float> =
        sideBar + point.x * shownWidth to topBar + point.y * shownHeight
}
