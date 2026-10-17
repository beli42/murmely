package org.pixeldroid.media_editor.photoEdit.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemGeometryTest {

    private val eps = 1e-4f
    private fun p(x: Float, y: Float) = RelativePoint(x, y)

    /** 0.2 wide, twice as high as wide, in the middle */
    private val tall = StickerItem("s", 0.5f, 0.5f, width = 0.2f, aspect = 2f, id = 1)

    @Test fun turnedStickerIsHitWhereItIs() {
        // Square image: the sticker covers x 0.4..0.6, y 0.3..0.7
        assertTrue(StickerGeometry.contains(tall, p(0.5f, 0.68f), 1f))
        assertFalse(StickerGeometry.contains(tall, p(0.68f, 0.5f), 1f))
        // Turned 90°: now wide and low
        val turned = tall.copy(rotation = 90f)
        assertFalse(StickerGeometry.contains(turned, p(0.5f, 0.68f), 1f))
        assertTrue(StickerGeometry.contains(turned, p(0.68f, 0.5f), 1f))
        // The margin makes it easier to grab
        assertTrue(StickerGeometry.contains(tall, p(0.61f, 0.5f), 1f, margin = 0.02f))
    }

    @Test fun cornersFollowTheTurnOnAWideImage() {
        // Image twice as wide as high: 0.1 image widths are 0.2 of the height
        val square = StickerItem("s", 0.5f, 0.5f, width = 0.2f, id = 1)
        val handle = StickerGeometry.handle(square, 0.5f)
        assertEquals(0.6f, handle.x, eps)
        assertEquals(0.7f, handle.y, eps)
        // Turned 90° clockwise the bottom right corner is at the bottom left
        val turned = StickerGeometry.handle(square.copy(rotation = 90f), 0.5f)
        assertEquals(0.4f, turned.x, eps)
        assertEquals(0.7f, turned.y, eps)
    }

    @Test fun handleScalesAndTurnsAroundTheCentre() {
        val square = StickerItem("s", 0.5f, 0.5f, width = 0.2f, id = 1)
        // Handle from the corner (0.6, 0.6) to twice as far out: twice as big, same angle
        val bigger = StickerGeometry.scaleAndTurn(square, p(0.6f, 0.6f), p(0.7f, 0.7f), 1f)
        assertEquals(0.4f, bigger.width, eps)
        assertEquals(0f, bigger.rotation, eps)
        assertEquals("centre stays", 0.5f to 0.5f, bigger.x to bigger.y)
        // A quarter around the centre, same distance: turned 90° clockwise
        val turned = StickerGeometry.scaleAndTurn(square, p(0.6f, 0.6f), p(0.4f, 0.6f), 1f)
        assertEquals(0.2f, turned.width, eps)
        assertEquals(90f, turned.rotation, eps)
        // Limits
        assertEquals(StickerGeometry.MIN_WIDTH, StickerGeometry.scaleAndTurn(square, p(0.6f, 0.6f), p(0.501f, 0.501f), 1f).width, eps)
        assertEquals(StickerGeometry.MAX_WIDTH, StickerGeometry.scaleAndTurn(square, p(0.6f, 0.6f), p(5f, 5f), 1f).width, eps)
    }

    @Test fun pinchMovesScalesAndTurns() {
        val square = StickerItem("s", 0.5f, 0.5f, width = 0.2f, id = 1)
        // Fingers horizontal 0.2 apart around the centre, then vertical 0.4 apart, shifted right
        val result = StickerGeometry.pinch(
            square,
            p(0.4f, 0.5f), p(0.6f, 0.5f),
            p(0.6f, 0.3f), p(0.6f, 0.7f),
            1f,
        )
        assertEquals("moved with the middle", 0.6f, result.x, eps)
        assertEquals(0.5f, result.y, eps)
        assertEquals("twice the distance", 0.4f, result.width, eps)
        assertEquals("turned with the fingers", 90f, result.rotation, eps)
    }

    @Test fun moveStaysOnTheImage() {
        val moved = StickerGeometry.move(tall, p(0.5f, 0.5f), p(0.7f, 0.4f))
        assertEquals(0.7f, moved.x, eps)
        assertEquals(0.4f, moved.y, eps)
        assertEquals(1f, StickerGeometry.move(tall, p(0f, 0f), p(2f, 0f)).x, eps)
    }

    @Test fun anglesStayBetweenMinusAndPlus180() {
        assertEquals(-170f, StickerGeometry.normalise(190f), eps)
        assertEquals(170f, StickerGeometry.normalise(-190f), eps)
        assertEquals(180f, StickerGeometry.normalise(-180f), eps)
        assertEquals(0f, StickerGeometry.normalise(720f), eps)
    }

    @Test fun transformChangesOnlyThatSticker() {
        val other = tall.copy(id = 2)
        val overlay = Overlay(stickers = listOf(tall, other)).transformSticker(1, 0.3f, 0.4f, 0.5f, 45f)
        assertEquals(StickerItem("s", 0.3f, 0.4f, 0.5f, 2f, 1, 45f), overlay.stickers[0])
        assertEquals(other, overlay.stickers[1])
    }

    @Test fun binIsAtTheBottomCentre() {
        // 1000 x 800 view, radius 50, 20 from the bottom: centre (500, 730)
        assertEquals(500f to 730f, StickerBin.centre(1000, 800, 50f, 20f))
        assertTrue(StickerBin.contains(500f, 730f, 1000, 800, 50f, 20f))
        assertTrue("a little larger than drawn", StickerBin.contains(560f, 730f, 1000, 800, 50f, 20f))
        assertFalse(StickerBin.contains(500f, 600f, 1000, 800, 50f, 20f))
        // A very low view: the bin stays inside
        assertEquals(50f, StickerBin.centre(1000, 60, 50f, 20f).second, eps)
    }

    @Test fun removingOnlyRemovesThatSticker() {
        val other = tall.copy(id = 2)
        assertEquals(listOf(other), Overlay(stickers = listOf(tall, other)).removeSticker(1).stickers)
        assertEquals(2, Overlay(stickers = listOf(tall, other)).removeSticker(99).stickers.size)
    }

    @Test fun textScalesWithItsBoxAndStaysWithinLimits() {
        val text = TextItem("Hi", 0.5f, 0.5f, size = 0.1f, id = 3)
        val start = TextGeometry.box(text, 0.2f)
        assertEquals(0.12f, start.height, eps)
        // Handle twice as far from the centre: twice as large, turned with the handle
        val corner = ItemGeometry.handle(start, 1f)
        val bigger = ItemGeometry.scaleAndTurn(start, corner, p(0.5f + 2 * (corner.x - 0.5f), 0.5f + 2 * (corner.y - 0.5f)), 1f)
        val result = TextGeometry.withBox(text, start, bigger)
        assertEquals(0.2f, result.size, eps)
        assertEquals(0f, result.rotation, eps)
        // Limits
        val huge = ItemGeometry.scaleAndTurn(start, corner, p(50f, 50f), 1f)
        assertEquals(TextGeometry.MAX_SIZE, TextGeometry.withBox(text, start, huge).size, eps)
    }

    @Test fun removingAndTransformingTexts() {
        val a = TextItem("A", 0.1f, 0.1f, id = 1)
        val b = TextItem("B", 0.2f, 0.2f, id = 2)
        val overlay = Overlay(texts = listOf(a, b))
        assertEquals(listOf(b), overlay.removeText(1).texts)
        assertEquals(b.copy(x = 0.3f, y = 0.4f, size = 0.2f, rotation = 30f), overlay.transformText(2, 0.3f, 0.4f, 0.2f, 30f).texts[1])
    }

    @Test fun boxesStayOnTheImage() {
        // 0.4 wide, 0.1 high, on a square image
        val box = ItemBox(0.5f, 0.5f, 0.4f, 0.1f)
        assertEquals("inside: unchanged", box, ItemGeometry.keepInside(box, 1f))
        // Too far right: its right edge on the image border
        val right = ItemGeometry.keepInside(box.copy(x = 0.95f), 1f)
        assertEquals(0.8f, right.x, eps)
        assertEquals(0.4f, right.width, eps)
        // Too far down on a wide image (half as high as wide): bottom edge on the border
        val down = ItemGeometry.keepInside(box.copy(y = 0.99f), 0.5f)
        assertEquals(0.9f, down.y, eps)    // 0.05 image widths = 0.1 of the height
        // Turned 90°: now 0.4 high, so it moves further in from the bottom
        val turned = ItemGeometry.keepInside(box.copy(y = 0.95f, rotation = 90f), 1f)
        assertEquals(0.8f, turned.y, eps)
        assertEquals(0.4f, turned.width, eps)
        // Wider than the image: made smaller to fit
        val wide = ItemGeometry.keepInside(box.copy(width = 2f), 1f)
        assertEquals(1f, wide.width, eps)
        assertEquals(0.05f, wide.height, eps)
        assertEquals(0.5f, wide.x, eps)
    }

    @Test fun textNearTheBorderMovesIn() {
        val text = TextItem("Hallo", 0.98f, 0.5f, size = 0.1f, id = 1)
        // 0.4 wide: centre at most 0.8 from the left
        val inside = TextGeometry.keepInside(text, 0.4f, 1f)
        assertEquals(0.8f, inside.x, eps)
        assertEquals("size kept", 0.1f, inside.size, eps)
        // 2 image widths long: half the size, centred
        val long = TextGeometry.keepInside(text, 2f, 1f)
        assertEquals(0.05f, long.size, eps)
        assertEquals(0.5f, long.x, eps)
    }

    @Test fun effectsMakeTheTextsBoxLarger() {
        val plain = TextItem("Hi", 0.5f, 0.5f, size = 0.1f, id = 1)
        val plainBox = TextGeometry.box(plain, 0.3f)
        assertEquals("no effects: just the letters", 0.3f, plainBox.width, eps)
        assertEquals(0.12f, plainBox.height, eps)
        // Outline 20 % of the size: 0.02 on every side
        val outlined = plain.copy(effects = TextEffects(outline = 0.2f))
        val box = TextGeometry.box(outlined, 0.3f)
        assertEquals(0.34f, box.width, eps)
        assertEquals(0.16f, box.height, eps)
        // Drop shadow straight right (0°), 0.3 of the size: only wider
        val dropped = TextGeometry.box(plain.copy(effects = TextEffects(dropShadow = 0.3f, dropShadowAngle = 0f)), 0.3f)
        assertEquals(0.36f, dropped.width, eps)
        assertEquals(0.12f, dropped.height, eps)
        // Scaling keeps the effects proportional
        val bigger = TextGeometry.withBox(outlined, box, box.copy(width = box.width * 2, height = box.height * 2))
        assertEquals(0.2f, bigger.size, eps)
    }
}
