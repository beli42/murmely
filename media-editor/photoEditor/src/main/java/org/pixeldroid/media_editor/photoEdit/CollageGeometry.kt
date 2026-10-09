package org.pixeldroid.media_editor.photoEdit

/** How each image is fitted into its area of a collage */
enum class CollageScaleMode {
    /** Fills the area, aspect ratio changed to match (default) */
    STRETCH,
    /** Whole image, original aspect ratio, white bars */
    FIT,
    /** Fills the area, original aspect ratio, overflow cropped */
    CROP;

    /** STRETCH -> FIT -> CROP -> STRETCH */
    fun next(): CollageScaleMode = entries[(ordinal + 1) % entries.size]
}

/** Integer rectangle, independent of android.graphics so it can be unit tested */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Float rectangle where an image is drawn */
data class Placement(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * Geometry of the collage layouts. Pure functions, no Android dependencies.
 *
 * Layouts: 1 = big image with a small one (40%) in the top left corner, 2 = left | right,
 * 3 = top / bottom, 4 = 2 x 2 grid. Images are numbered from 1.
 */
object CollageGeometry {

    /** Number of images a layout takes */
    fun imageCount(layout: Int): Int = if (layout == 4) 4 else 2

    /** Area of image [image] in a [width] x [height] collage of [layout] */
    fun cell(layout: Int, image: Int, width: Int, height: Int): Box = when (layout) {
        1 -> if (image == 1) Box(0, 0, width, height)
             else Box(0, 0, (width * 0.4).toInt(), (height * 0.4).toInt())
        2 -> if (image == 1) Box(0, 0, width / 2, height) else Box(width / 2, 0, width, height)
        3 -> if (image == 1) Box(0, 0, width, height / 2) else Box(0, height / 2, width, height)
        else -> {
            val left = if (image % 2 == 1) 0 else width / 2
            val top = if (image <= 2) 0 else height / 2
            Box(left, top, left + width / 2, top + height / 2)
        }
    }

    /**
     * Size an image of [imageWidth] x [imageHeight] is loaded at for a cell: for CROP exactly
     * the cell, otherwise as large as fits into the cell with the original aspect ratio.
     */
    fun loadedSize(cell: Box, imageWidth: Int, imageHeight: Int, mode: CollageScaleMode): Pair<Int, Int> {
        if (mode == CollageScaleMode.CROP) return cell.width to cell.height
        val scale = minOf(cell.width.toFloat() / imageWidth, cell.height.toFloat() / imageHeight)
        return Math.round(imageWidth * scale) to Math.round(imageHeight * scale)
    }

    /**
     * Where a loaded image of [loadedWidth] x [loadedHeight] is drawn in [cell]:
     * STRETCH fills the cell exactly, FIT and CROP are centered at their own size.
     */
    fun placement(cell: Box, loadedWidth: Int, loadedHeight: Int, mode: CollageScaleMode): Placement =
        if (mode == CollageScaleMode.STRETCH) {
            Placement(cell.left.toFloat(), cell.top.toFloat(), cell.right.toFloat(), cell.bottom.toFloat())
        } else {
            val left = cell.left + (cell.width - loadedWidth) / 2f
            val top = cell.top + (cell.height - loadedHeight) / 2f
            Placement(left, top, left + loadedWidth, top + loadedHeight)
        }
}
