package org.pixeldroid.media_editor.videoEdit

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Converts a real test video (made from a still image with Media3) with different edits and
 * measures the result: length, size, and that nothing is left behind when it fails or is cancelled.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class VideoEncoderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dir = File(context.cacheDir, "video-encoder-test")
    private lateinit var source: Uri

    private val sourceSeconds = 2f
    private val sourceWidth = 320
    private val sourceHeight = 240

    @Before fun createSourceVideo() {
        dir.mkdirs()
        source = createVideo("source.mp4", sourceSeconds, sourceWidth, sourceHeight)
    }

    /** A video of [seconds] made from a still image, encoded by Media3 itself */
    private fun createVideo(name: String, seconds: Float, width: Int, height: Int): Uri {
        val image = File(dir, "$name.png")
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .let { bitmap -> image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }

        val video = File(dir, name)
        val done = CountDownLatch(1)
        var error: ExportException? = null
        instrumentation.runOnMainSync {
            val item = MediaItem.Builder().setUri(image.toUri()).setImageDurationMs((seconds * 1000).toLong()).build()
            Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        error = exportException
                        done.countDown()
                    }
                })
                .build()
                .start(EditedMediaItem.Builder(item).setFrameRate(30).build(), video.absolutePath)
        }
        assertTrue("creating the test video timed out", done.await(120, TimeUnit.SECONDS))
        error?.let { throw AssertionError("could not create the test video", it) }
        return video.toUri()
    }

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    private data class Result(val output: Uri?, val error: Boolean, val progress: List<Int>)

    /**
     * Runs a conversion to [target] and waits for the end. With [cancelWhileRunning] it is
     * cancelled once it reports progress, like a user tapping "cancel" during the conversion.
     */
    private fun encode(arguments: VideoEditArguments, target: Uri, cancelWhileRunning: Boolean = false): Result {
        val done = CountDownLatch(1)
        val running = CountDownLatch(1)
        var output: Uri? = null
        var error = false
        val progress = mutableListOf<Int>()
        var session = -1L
        instrumentation.runOnMainSync {
            VideoEncoder.start(
                context, source, target, arguments,
                registerSession = { _, id -> session = id },
                trackTempFile = {},
                progress = { _, percent, _, out, failed ->
                    progress += percent
                    // Only an intermediate progress means "running" (the end also reports 100 %)
                    if (out == null) running.countDown()
                    if (out != null) {
                        output = out
                        error = failed
                        done.countDown()
                    }
                },
            )
        }
        if (cancelWhileRunning) {
            assertTrue("conversion did not start", running.await(30, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { VideoEncoder.cancel(session) }
            // Give a late callback the chance to show up (there must be none)
            Thread.sleep(1000)
        } else {
            assertTrue("conversion timed out", done.await(120, TimeUnit.SECONDS))
        }
        return Result(output, error, progress)
    }

    private fun target(name: String): File = File(dir, name).apply { createNewFile() }   // like "save as" does

    private fun args(
        start: Float? = null, end: Float? = null, speedIndex: Int = VideoEditPlan.NORMAL_SPEED_INDEX,
        muted: Boolean = false, crop: RelativeCropPosition = RelativeCropPosition(),
    ) = VideoEditArguments(muted, start, end, speedIndex, crop)

    private class Measured(val seconds: Float, val width: Int, val height: Int, val hasAudio: Boolean)

    /**
     * Length and the size as shown. Transformer may encode a portrait video in landscape plus a
     * 90° rotation flag (encoders handle landscape better); players show it rotated back.
     */
    private fun measure(file: File): Measured = MediaMetadataRetriever().use { r ->
        r.setDataSource(file.absolutePath)
        val storedWidth = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        val storedHeight = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val sideways = rotation % 180 != 0
        Measured(
            seconds = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() / 1000f,
            width = if (sideways) storedHeight else storedWidth,
            height = if (sideways) storedWidth else storedHeight,
            hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
        )
    }

    private fun assertSeconds(expected: Float, actual: Float, what: String) =
        assertEquals(what, expected, actual, 0.2f)   // frame and container rounding

    @Test fun unchangedVideoIsConverted() {
        val out = target("plain.mp4")
        val result = encode(args(), out.toUri())
        assertFalse("conversion failed", result.error)
        assertTrue("output is empty", out.length() > 0)
        val m = measure(out)
        assertSeconds(sourceSeconds, m.seconds, "length")
        assertEquals(sourceWidth to sourceHeight, m.width to m.height)
    }

    @Test fun trimmedVideoHasTheChosenLength() {
        val out = target("trim.mp4")
        assertFalse(encode(args(start = 0.5f, end = 1.5f), out.toUri()).error)
        assertSeconds(1f, measure(out).seconds, "trimmed length")
    }

    @Test fun doubleSpeedHalvesTheLength() {
        val out = target("speed.mp4")
        val arguments = args(speedIndex = VideoEditPlan.SPEEDS.indexOf(2f))
        assertFalse(encode(arguments, out.toUri()).error)
        val m = measure(out)
        assertSeconds(VideoEditPlan.outputSeconds(sourceSeconds, arguments), m.seconds, "length at 2x")
        assertFalse("speed change removes audio", m.hasAudio)
    }

    @Test fun croppedVideoHasTheCroppedSize() {
        val out = target("crop.mp4")
        assertFalse(encode(args(crop = RelativeCropPosition(relativeWidth = 0.5f)), out.toUri()).error)
        val m = measure(out)
        assertEquals("cropped width", sourceWidth / 2.0, m.width.toDouble(), 2.0)
        assertEquals("height unchanged", sourceHeight.toDouble(), m.height.toDouble(), 2.0)
    }

    @Test fun progressIsReported() {
        val result = encode(args(), target("progress.mp4").toUri())
        assertNotNull(result.output)
        assertEquals("ends at 100 %", 100, result.progress.last())
    }

    /** Regression: a failed conversion left an empty file behind */
    @Test fun failedConversionRemovesTheTarget() {
        source = File(dir, "does-not-exist.mp4").toUri()
        val out = target("failed.mp4")
        val result = encode(args(), out.toUri())
        assertTrue("reported as failed", result.error)
        assertFalse("empty target was removed", out.exists())
    }

    @Test fun cancelledConversionRemovesTheTarget() {
        // A longer, larger video, so the conversion is still running when it is cancelled
        source = createVideo("long.mp4", 10f, 1280, 720)
        val out = target("cancelled.mp4")
        val result = encode(args(), out.toUri(), cancelWhileRunning = true)
        // If the device was faster than the cancel, there was nothing left to cancel
        assumeTrue("conversion finished before it could be cancelled", result.output == null)
        assertFalse("target was removed", out.exists())
    }
}
