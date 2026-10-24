package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.pixeldroid.media_editor.photoEdit.info.ImageInfoText

class ImageInfoTextTest {

    @Test fun fileSizes() {
        assertEquals("512 B", ImageInfoText.fileSize(512))
        assertEquals("820 KB", ImageInfoText.fileSize(820_000))
        assertEquals("2.4 MB", ImageInfoText.fileSize(2_412_345))
    }

    @Test fun resolutionWithMegapixels() {
        assertEquals("4000 × 3000 (12.0 MP)", ImageInfoText.resolution(4000, 3000))
        assertEquals("800 × 600", ImageInfoText.resolution(800, 600))
    }

    @Test fun formatDateAndCamera() {
        assertEquals("JPEG", ImageInfoText.format("image/jpeg"))
        assertEquals("2024-05-01 12:34", ImageInfoText.taken("2024:05:01 12:34:56"))
        assertEquals("not a date", ImageInfoText.taken("not a date"))
        assertEquals("Google Pixel 7", ImageInfoText.camera("Google", "Pixel 7"))
        assertEquals("the make not twice", "Samsung SM-S911B", ImageInfoText.camera("samsung", "Samsung SM-S911B"))
        assertNull(ImageInfoText.camera(null, " "))
    }

    @Test fun folderOfAPath() {
        assertEquals("/storage/emulated/0/DCIM/Camera", ImageInfoText.folder("/storage/emulated/0/DCIM/Camera/PXL_1.jpg"))
        assertEquals("/", ImageInfoText.folder("/x.jpg"))
    }
}
