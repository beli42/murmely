package eu.artectrex.bunny

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveLocationTextTest {

    @Test fun internalStorage() {
        assertEquals("Pictures/Bunny", SaveLocationText.describeDocumentId("primary:Pictures/Bunny"))
        assertEquals("DCIM", SaveLocationText.describeDocumentId("primary:DCIM"))
    }

    @Test fun rootOfInternalStorage() = assertEquals("/", SaveLocationText.describeDocumentId("primary:"))

    @Test fun sdCardShowsVolume() =
        assertEquals("1234-ABCD: Fotos/Bearbeitet", SaveLocationText.describeDocumentId("1234-ABCD:Fotos/Bearbeitet"))

    @Test fun pathWithColons() =
        assertEquals("Pictures/a:b", SaveLocationText.describeDocumentId("primary:Pictures/a:b"))

    @Test fun qualityIsClamped() {
        assertEquals(93, SaveLocationText.clampQuality(93))
        assertEquals(10, SaveLocationText.clampQuality(0))
        assertEquals(10, SaveLocationText.clampQuality(-5))
        assertEquals(100, SaveLocationText.clampQuality(150))
    }

    @Test fun onlyContentUrisAreAcceptedFromOtherApps() {
        assertTrue(SaveLocationText.isAcceptedSharedScheme("content"))
        assertFalse(SaveLocationText.isAcceptedSharedScheme("file"))
        assertFalse(SaveLocationText.isAcceptedSharedScheme("http"))
        assertFalse(SaveLocationText.isAcceptedSharedScheme(null))
    }
}
