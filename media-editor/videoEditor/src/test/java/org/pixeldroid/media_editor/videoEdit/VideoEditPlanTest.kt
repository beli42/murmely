package org.pixeldroid.media_editor.videoEdit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEditPlanTest {

    private fun args(
        muted: Boolean = false, start: Float? = null, end: Float? = null,
        speedIndex: Int = VideoEditPlan.NORMAL_SPEED_INDEX, crop: RelativeCropPosition = RelativeCropPosition(),
    ) = VideoEditArguments(muted, start, end, speedIndex, crop)

    @Test fun speeds() {
        assertEquals(1f, VideoEditPlan.speed(args()))
        assertEquals(0.5f, VideoEditPlan.speed(args(speedIndex = 0)))
        assertEquals(8f, VideoEditPlan.speed(args(speedIndex = 4)))
        assertEquals("unknown index falls back to normal speed", 1f, VideoEditPlan.speed(args(speedIndex = 99)))
    }

    @Test fun audioIsRemovedWhenMutedOrSpeedChanges() {
        assertFalse(VideoEditPlan.removeAudio(args()))
        assertTrue(VideoEditPlan.removeAudio(args(muted = true)))
        assertTrue(VideoEditPlan.removeAudio(args(speedIndex = 2)))
    }

    @Test fun clipping() {
        assertEquals(0L to null, VideoEditPlan.clippingMs(args()))
        assertEquals(1500L to 4250L, VideoEditPlan.clippingMs(args(start = 1.5f, end = 4.25f)))
        assertEquals("end before start is ignored", 3000L to null, VideoEditPlan.clippingMs(args(start = 3f, end = 2f)))
        assertEquals("negative start", 0L to 1000L, VideoEditPlan.clippingMs(args(start = -1f, end = 1f)))
    }

    @Test fun noCropCoversTheWholeFrame() =
        assertEquals(VideoEditPlan.Ndc(-1f, 1f, -1f, 1f), VideoEditPlan.cropNdc(RelativeCropPosition()))

    @Test fun cropLeftHalf() =
        assertEquals(VideoEditPlan.Ndc(-1f, 0f, -1f, 1f), VideoEditPlan.cropNdc(RelativeCropPosition(relativeWidth = 0.5f)))

    @Test fun cropTopRightQuarter() = assertEquals(
        VideoEditPlan.Ndc(0f, 1f, 0f, 1f),
        VideoEditPlan.cropNdc(RelativeCropPosition(0.5f, 0.5f, relativeX = 0.5f, relativeY = 0f))
    )

    @Test fun cropBottomCentre() {
        val ndc = VideoEditPlan.cropNdc(RelativeCropPosition(0.5f, 0.25f, relativeX = 0.25f, relativeY = 0.75f))
        assertEquals(-0.5f, ndc.left, 1e-6f)
        assertEquals(0.5f, ndc.right, 1e-6f)
        assertEquals(-1f, ndc.bottom, 1e-6f)
        assertEquals(-0.5f, ndc.top, 1e-6f)
    }

    @Test fun cropIsClampedToTheFrame() {
        val ndc = VideoEditPlan.cropNdc(RelativeCropPosition(0.8f, 0.8f, relativeX = 0.5f, relativeY = 0.5f))
        assertEquals(1f, ndc.right, 0f)
        assertEquals(-1f, ndc.bottom, 0f)
    }

    @Test fun outputLength() {
        assertEquals(10f, VideoEditPlan.outputSeconds(10f, args()), 1e-4f)
        assertEquals(3f, VideoEditPlan.outputSeconds(10f, args(start = 2f, end = 5f)), 1e-4f)
        assertEquals(5f, VideoEditPlan.outputSeconds(10f, args(speedIndex = 2)), 1e-4f)
        assertEquals(20f, VideoEditPlan.outputSeconds(10f, args(speedIndex = 0)), 1e-4f)
        assertEquals("end beyond the video", 8f, VideoEditPlan.outputSeconds(10f, args(start = 2f, end = 30f)), 1e-4f)
        assertEquals(1.5f, VideoEditPlan.outputSeconds(10f, args(start = 2f, end = 8f, speedIndex = 3)), 1e-4f)
    }

    @Test fun notCropped() {
        assertTrue(RelativeCropPosition().notCropped())
        assertTrue(RelativeCropPosition(0.9999f, 1f, 0.0001f, 0f).notCropped())
        assertFalse(RelativeCropPosition(relativeWidth = 0.5f).notCropped())
    }

    @Test fun unchangedEditIsNotClipped() = assertNull(VideoEditPlan.clippingMs(args()).second)
}
