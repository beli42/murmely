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
 * Where the lines between the areas are, set with sliders after the photos are picked:
 * [x] the vertical line (side by side, 2 x 2), [y] the horizontal one (one above the other,
 * 2 x 2), both as parts of the side (0..1); [middle] how much larger the middle of the 3 x 3
 * grid is than its other rows and columns
 */
data class CollageSplit(
    val x: Double = 0.5,
    val y: Double = 0.5,
    val middle: Double = CollageGeometry.GRID_MIDDLE,
) {
    companion object {
        /** How far the lines of the 2 x 1, 1 x 2 and 2 x 2 layouts can be moved */
        val LINE_RANGE = 0.2..0.8
    }
}

/**
 * Geometry of the collage layouts. Pure functions, no Android dependencies.
 *
 * Layouts: see [LAYOUTS]. Images are numbered from 1.
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

    /**
     * In the 3 x 3 grid the middle row and column are this much larger than the others: at first
     * twice (half the side), adjustable from [GRID_MIDDLE_MIN] (half as large as the others; 1 is
     * all the same) to [GRID_MIDDLE_MAX]
     */
    const val GRID_MIDDLE = 2.0
    const val GRID_MIDDLE_MIN = 0.5
    const val GRID_MIDDLE_MAX = 4.0

    /** Edges of the 3 x 3 grid's rows and columns, in parts of the side (0..1) */
    private fun gridEdges(middle: Double): List<Double> {
        val small = 1.0 / (2 + middle)
        return listOf(0.0, small, small + small * middle, 1.0)
    }

    /** Area of image [image] in a [width] x [height] collage of [layout], its lines at [split] */
    fun cell(layout: Int, image: Int, width: Int, height: Int, split: CollageSplit = CollageSplit()): Box {
        val x = Math.round(split.x * width).toInt()
        val y = Math.round(split.y * height).toInt()
        return when (layout) {
            2 -> if (image == 1) Box(0, 0, x, height) else Box(x, 0, width, height)
            3 -> if (image == 1) Box(0, 0, width, y) else Box(0, y, width, height)
            5 -> gridCell(image, width, height, split.middle)
            else -> {
                val left = if (image % 2 == 1) 0 else x
                val top = if (image <= 2) 0 else y
                Box(left, top, if (image % 2 == 1) x else width, if (image <= 2) y else height)
            }
        }
    }

    /** Area of image [image] in the 3 x 3 grid, row by row from the top left */
    private fun gridCell(image: Int, width: Int, height: Int, middle: Double): Box {
        // The last edges are the full size, so no gaps
        val column = (image - 1) % 3
        val row = (image - 1) / 3
        val edges = gridEdges(middle)
        fun at(i: Int, size: Int) = if (i == 3) size else Math.round(edges[i] * size).toInt()
        return Box(at(column, width), at(row, height), at(column + 1, width), at(row + 1, height))
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
