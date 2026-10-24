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

    /**
     * The layouts: 2 side by side, 3 one above the other, 4 a 2 x 2 grid, 5 a 3 x 3 grid with a
     * larger middle (layout 1, a small image on a large one, was removed: a sticker does that,
     * placed anywhere)
     */
    val LAYOUTS = listOf(2, 3, 4, 5)

    /** Number of images a layout takes */
    fun imageCount(layout: Int): Int = when (layout) {
        4 -> 4
        5 -> 9
        else -> 2
    }

    /** In the 3 x 3 grid the middle row and column are this much larger than the others: half the side */
    const val GRID_MIDDLE = 2.0

    /** Edges of the 3 x 3 grid's rows and columns, in parts of the side (0..1) */
    private val gridEdges: List<Double> = run {
        val small = 1.0 / (2 + GRID_MIDDLE)
        listOf(0.0, small, small + small * GRID_MIDDLE, 1.0)
    }

    /** Area of image [image] in a [width] x [height] collage of [layout] */
    fun cell(layout: Int, image: Int, width: Int, height: Int): Box = when (layout) {
        2 -> if (image == 1) Box(0, 0, width / 2, height) else Box(width / 2, 0, width, height)
        3 -> if (image == 1) Box(0, 0, width, height / 2) else Box(0, height / 2, width, height)
        5 -> {
            // Row by row from the top left; the last edges are the full size, so no gaps
            val column = (image - 1) % 3
            val row = (image - 1) / 3
            fun at(i: Int, size: Int) = if (i == 3) size else Math.round(gridEdges[i] * size).toInt()
            Box(at(column, width), at(row, height), at(column + 1, width), at(row + 1, height))
        }
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

    /**
     * The areas of [layout] in the order photos picked together fill them (and their numbers):
     * from the top left clockwise, the 3 x 3 grid around the outside, its middle last
     */
    fun fillOrder(layout: Int): List<Int> = when (layout) {
        4 -> listOf(1, 2, 4, 3)
        5 -> listOf(1, 2, 3, 6, 9, 8, 7, 4, 5)
        else -> (1..imageCount(layout)).toList()
    }
}
