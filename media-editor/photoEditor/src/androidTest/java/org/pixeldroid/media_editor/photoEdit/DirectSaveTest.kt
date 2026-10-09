package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Saving through DirectSave into a real MediaStore folder, checking every written file */
@RunWith(AndroidJUnit4::class)
class DirectSaveTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = TestFolder(context)

    @After fun cleanUp() = folder.deleteAll()

    private fun directSave(format: SaveFormat) = DirectSave(context, folder.name, null, format, 93)

    private fun save(format: SaveFormat, name: String, bitmap: Bitmap): Uri {
        val ds = directSave(format)
        val uri = ds.create { used -> FileNaming.numberedName(name, used, format.extension) }
        assertNotNull("file could not be created", uri)
        context.contentResolver.openOutputStream(uri!!)!!.writeBitmap(bitmap, format, 93)
        ds.finish(uri)
        return uri
    }

    @Test fun pngIsCompleteAndLossless() {
        val source = TestImages.gradient(1200, 800)
        val saved = ImageIntegrity.assertComplete(context, save(SaveFormat.PNG, "photo", source), SaveFormat.PNG, 1200, 800)
        for (y in 0 until 800 step 37) for (x in 0 until 1200 step 41) {
            assertEquals("pixel ($x, $y)", source.getPixel(x, y), saved.getPixel(x, y))
        }
    }

    @Test fun jpegIsCompleteAndClose() {
        val source = TestImages.gradient(1200, 800)
        val saved = ImageIntegrity.assertComplete(context, save(SaveFormat.JPEG, "photo", source), SaveFormat.JPEG, 1200, 800)
        for (y in 20 until 780 step 53) for (x in 20 until 1180 step 59) {
            ImageIntegrity.assertColor(saved, x, y, source.getPixel(x, y), 16, "JPEG pixel")
        }
    }

    @Test fun largeImagesAreComplete() {
        for (format in SaveFormat.entries) {
            val uri = save(format, "large", TestImages.gradient(4000, 3000))
            ImageIntegrity.assertComplete(context, uri, format, 4000, 3000)
        }
    }

    @Test fun savedFilesAreNumbered() {
        val names = (1..3).map { folder.displayName(save(SaveFormat.PNG, "photo", TestImages.solid(10, 10, Color.RED))) }
        assertEquals(listOf("photo-1.png", "photo-2.png", "photo-3.png"), names)
    }

    /** Regression: a double tap created two files with the same name, one of them left broken */
    @Test fun twoSavesStartedTogetherGetDifferentNamesAndAreBothComplete() {
        val first = directSave(SaveFormat.JPEG)
        val second = directSave(SaveFormat.JPEG)
        val uri1 = first.create { FileNaming.nameWithOptionalNumber("collage-test", it, "jpg") }!!
        val uri2 = second.create { FileNaming.nameWithOptionalNumber("collage-test", it, "jpg") }!!

        val name1 = folder.displayName(uri1)
        val name2 = folder.displayName(uri2)
        assertNotEquals(name1, name2)
        assertFalse("Android had to rename: $name2", '~' in name2 || '(' in name2)

        for ((ds, uri) in listOf(first to uri1, second to uri2)) {
            context.contentResolver.openOutputStream(uri)!!.writeBitmap(TestImages.gradient(800, 600), SaveFormat.JPEG, 93)
            ds.finish(uri)
            ImageIntegrity.assertComplete(context, uri, SaveFormat.JPEG, 800, 600)
        }
        assertEquals(setOf("collage-test.jpg", "collage-test-1.jpg"), folder.files().keys)
    }

    @Test fun discardRemovesTheHalfWrittenFile() {
        val ds = directSave(SaveFormat.PNG)
        val uri = ds.create { FileNaming.numberedName("broken", it) }!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(ByteArray(100) { 1 }) }
        ds.discard()
        assertTrue("left behind: ${folder.files().keys}", folder.files().isEmpty())
    }

    @Test fun transparentAreasBecomeWhiteInJpeg() {
        val source = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (y in 100 until 300) for (x in 100 until 300) setPixel(x, y, Color.RED)
        }
        val saved = ImageIntegrity.assertComplete(context, save(SaveFormat.JPEG, "transparent", source), SaveFormat.JPEG, 400, 400)
        ImageIntegrity.assertColor(saved, 20, 20, Color.WHITE, 8, "former transparent area")
        ImageIntegrity.assertColor(saved, 200, 200, Color.RED, 16, "opaque area")
    }
}
