package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FileNamingTest {

    // --- Edited photos: "<base>-<n>.<ext>" ---

    @Test fun firstEditGetsNumberOne() =
        assertEquals("IMG_1234-1.png", FileNaming.numberedName("IMG_1234", emptySet()))

    @Test fun takesFirstFreeNumber() = assertEquals(
        "IMG-3.png",
        FileNaming.numberedName("IMG", setOf("IMG-1.png", "IMG-2.png", "IMG-4.png"))
    )

    @Test fun editingAnEditedImageContinuesTheNumbering() {
        assertEquals("IMG_1234-1.png", FileNaming.numberedName("IMG_1234-1", emptySet()))
        assertEquals("IMG_1234-2.png", FileNaming.numberedName("IMG_1234-1", setOf("IMG_1234-1.png")))
        assertEquals("IMG_1234-1.png", FileNaming.numberedName("IMG_1234-123", emptySet()))
    }

    @Test fun datesAreNotMistakenForNumbers() {
        assertEquals("2026-10-09-1.png", FileNaming.numberedName("2026-10-09", emptySet()))
        assertEquals("2026-10-10-1.png", FileNaming.numberedName("2026-10-10", emptySet()))
        // A number after a date is ours again
        assertEquals("2026-10-10-1.png", FileNaming.numberedName("2026-10-10-1", emptySet()))
    }

    @Test fun longNumbersAndLeadingZerosAreKept() {
        assertEquals("IMG-1000-1.png", FileNaming.numberedName("IMG-1000", emptySet()))
        assertEquals("photo-0-1.png", FileNaming.numberedName("photo-0", emptySet()))
        assertEquals("PXL_20261009-07-1.png", FileNaming.numberedName("PXL_20261009-07", emptySet()))
    }

    @Test fun emptyBaseFallsBackToImage() =
        assertEquals("image-1.png", FileNaming.numberedName("-3", emptySet()))

    @Test fun numberingIsPerExtension() {
        assertEquals("IMG-1.jpg", FileNaming.numberedName("IMG", setOf("IMG-1.png"), "jpg"))
        assertEquals("IMG-2.jpg", FileNaming.numberedName("IMG", setOf("IMG-1.png", "IMG-1.jpg"), "jpg"))
    }

    // --- Collages: "<base>.<ext>", then "<base>-<n>.<ext>" ---

    @Test fun collageWithoutNumberIfFree() = assertEquals(
        "collage-2026-10-09.png", FileNaming.nameWithOptionalNumber("collage-2026-10-09", emptySet())
    )

    @Test fun collageGetsNumbersWhenTaken() {
        val base = "collage-2026-10-09"
        assertEquals("$base-1.png", FileNaming.nameWithOptionalNumber(base, setOf("$base.png")))
        assertEquals(
            "$base-3.jpg",
            FileNaming.nameWithOptionalNumber(base, setOf("$base.jpg", "$base-1.jpg", "$base-2.jpg"), "jpg")
        )
    }

    @Test fun neverReturnsAUsedName() {
        val used = HashSet<String>()
        repeat(50) {
            val name = FileNaming.nameWithOptionalNumber("c", used)
            assertTrue("$name was already used", name !in used)
            used += name
        }
        assertEquals(50, used.size)
    }

    @Test fun todayIsIsoDate() {
        val date = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 7) }.time
        assertEquals("2026-03-07", FileNaming.today(date))
    }

    // --- Format ---

    @Test fun formatFromName() {
        assertEquals(SaveFormat.JPEG, SaveFormat.fromName("jpeg"))
        assertEquals(SaveFormat.JPEG, SaveFormat.fromName("JPG"))
        assertEquals(SaveFormat.PNG, SaveFormat.fromName("png"))
        assertEquals(SaveFormat.PNG, SaveFormat.fromName(null))
        assertEquals(SaveFormat.PNG, SaveFormat.fromName("gif"))
    }

    @Test fun formatProperties() {
        assertEquals("image/png" to "png", SaveFormat.PNG.mimeType to SaveFormat.PNG.extension)
        assertEquals("image/jpeg" to "jpg", SaveFormat.JPEG.mimeType to SaveFormat.JPEG.extension)
        assertEquals(93, SaveFormat.DEFAULT_JPEG_QUALITY)
    }
}
