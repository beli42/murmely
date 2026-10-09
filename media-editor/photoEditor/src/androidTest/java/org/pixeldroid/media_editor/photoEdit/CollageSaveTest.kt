package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders and saves collages of every layout, fitting mode and file format, and checks that each
 * file is complete and that every image ended up where it belongs:
 * - each source image is a solid colour, so the centre of its area must have that colour
 * - FIT: the bars around an image must be white
 * - CROP / STRETCH: the image must reach the corners of its area
 */
@RunWith(AndroidJUnit4::class)
class CollageSaveTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = TestFolder(context)

    /** Different aspect ratios, so the fitting modes really differ */
    private data class Source(val width: Int, val height: Int, val color: Int)
    private val sources = listOf(
        Source(1600, 900, Color.RED),       // landscape
        Source(900, 1600, Color.GREEN),     // portrait
        Source(1000, 1000, Color.BLUE),     // square
        Source(1200, 800, Color.YELLOW),    // landscape 3:2
    )
    private lateinit var uris: List<Uri>

    @Before fun createSources() {
        uris = sources.mapIndexed { i, s -> TestImages.write(context, "source-$i.png", TestImages.solid(s.width, s.height, s.color)) }
    }

    @After fun cleanUp() = folder.deleteAll()

    @Test fun allLayoutsModesAndFormatsAreSavedCompletelyAndCorrectly() = runBlocking {
        for (format in SaveFormat.entries) for (layout in 1..4) for (mode in CollageScaleMode.entries) {
            val what = "$format layout $layout $mode"
            val count = CollageGeometry.imageCount(layout)
            val images = (1..count).associateWith { uris[it - 1] }

            val bitmap = CollageRenderer.render(context, images, layout, mode)
            val ds = DirectSave(context, folder.name, null, format, 93)
            val uri = ds.create { FileNaming.nameWithOptionalNumber("collage-test", it, format.extension) }!!
            context.contentResolver.openOutputStream(uri)!!.writeBitmap(bitmap, format, 93)
            ds.finish(uri)
            bitmap.recycle()

            val saved = ImageIntegrity.assertComplete(context, uri, format, CollageRenderer.SIZE, CollageRenderer.SIZE)
            checkContent(saved, layout, mode, count, if (format == SaveFormat.PNG) 4 else 24, what)
            saved.recycle()
        }
    }

    private fun checkContent(saved: Bitmap, layout: Int, mode: CollageScaleMode, count: Int, tolerance: Int, what: String) {
        val size = CollageRenderer.SIZE
        val cells = (1..count).map { CollageGeometry.cell(layout, it, size, size) }
        // Points covered by an image drawn later (the small inset of layout 1) are not checked
        fun coveredLater(image: Int, x: Int, y: Int) =
            cells.drop(image).any { x >= it.left && x < it.right && y >= it.top && y < it.bottom }

        for (image in 1..count) {
            val cell = cells[image - 1]
            val source = sources[image - 1]
            val (w, h) = CollageGeometry.loadedSize(cell, source.width, source.height, mode)
            val p = CollageGeometry.placement(cell, w, h, mode)
            val label = "$what image $image"

            // Centre of the visible part of the image
            val cx = ((maxOf(p.left, cell.left.toFloat()) + minOf(p.right, cell.right.toFloat())) / 2).toInt()
            val cy = ((maxOf(p.top, cell.top.toFloat()) + minOf(p.bottom, cell.bottom.toFloat())) / 2).toInt()
            if (!coveredLater(image, cx, cy)) ImageIntegrity.assertColor(saved, cx, cy, source.color, tolerance, "$label centre")

            val margin = 12
            when (mode) {
                CollageScaleMode.FIT -> {
                    // Middle of the bar on each side where there is one
                    if (p.top - cell.top > 2 * margin)
                        check(saved, cell.left + cell.width / 2, ((cell.top + p.top) / 2).toInt(), image, Color.WHITE, tolerance, "$label top bar", ::coveredLater)
                    if (cell.bottom - p.bottom > 2 * margin)
                        check(saved, cell.left + cell.width / 2, ((cell.bottom + p.bottom) / 2).toInt(), image, Color.WHITE, tolerance, "$label bottom bar", ::coveredLater)
                    if (p.left - cell.left > 2 * margin)
                        check(saved, ((cell.left + p.left) / 2).toInt(), cell.top + cell.height / 2, image, Color.WHITE, tolerance, "$label left bar", ::coveredLater)
                    if (cell.right - p.right > 2 * margin)
                        check(saved, ((cell.right + p.right) / 2).toInt(), cell.top + cell.height / 2, image, Color.WHITE, tolerance, "$label right bar", ::coveredLater)
                }
                CollageScaleMode.CROP, CollageScaleMode.STRETCH -> {
                    // The image reaches every corner of its area
                    for ((x, y) in listOf(
                        cell.left + margin to cell.top + margin, cell.right - margin to cell.top + margin,
                        cell.left + margin to cell.bottom - margin, cell.right - margin to cell.bottom - margin,
                    )) check(saved, x, y, image, source.color, tolerance, "$label corner", ::coveredLater)
                }
            }
        }
    }

    private fun check(
        saved: Bitmap, x: Int, y: Int, image: Int, color: Int, tolerance: Int, what: String,
        coveredLater: (Int, Int, Int) -> Boolean,
    ) {
        if (!coveredLater(image, x, y)) ImageIntegrity.assertColor(saved, x, y, color, tolerance, what)
    }
}
