package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.math.abs

/**
 * Checks that a saved image file is complete and correct, independently of Android's decoder:
 * - PNG: signature, every chunk's length and CRC, IHDR first, IEND last, nothing after it
 * - JPEG: start/end markers, segment structure up to the image data, size from the frame header
 * Then it is decoded strictly (an incomplete file fails instead of being shown partially).
 */
object ImageIntegrity {

    data class Info(val width: Int, val height: Int)

    private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
    private fun ByteArray.u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
    private fun ByteArray.u32(i: Int) = (u16(i).toLong() shl 16) or u16(i + 2).toLong()

    private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    fun checkPng(bytes: ByteArray): Info {
        assertTrue("PNG too short (${bytes.size} bytes)", bytes.size > 8 + 25 + 12)
        assertTrue("not a PNG signature", bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE))
        var pos = 8
        var info: Info? = null
        var chunks = 0
        while (true) {
            assertTrue("PNG truncated: chunk header at $pos of ${bytes.size}", pos + 8 <= bytes.size)
            val length = bytes.u32(pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            assertTrue("PNG chunk $type at $pos truncated (length $length)", pos + 12 + length <= bytes.size)
            val crc = CRC32().apply { update(bytes, pos + 4, 4 + length.toInt()) }.value
            assertEquals("PNG CRC of chunk $type at $pos", crc, bytes.u32(pos + 8 + length.toInt()))
            if (chunks == 0) {
                assertEquals("first PNG chunk", "IHDR", type)
                info = Info(bytes.u32(pos + 8).toInt(), bytes.u32(pos + 12).toInt())
            }
            chunks++
            pos += 12 + length.toInt()
            if (type == "IEND") break
        }
        assertEquals("bytes after IEND", 0, bytes.size - pos)
        return info!!
    }

    fun checkJpeg(bytes: ByteArray): Info {
        assertTrue("JPEG too short (${bytes.size} bytes)", bytes.size > 4)
        assertTrue("no JPEG start marker (FFD8)", bytes.u8(0) == 0xFF && bytes.u8(1) == 0xD8)
        assertTrue(
            "no JPEG end marker (FFD9), file incomplete",
            bytes.u8(bytes.size - 2) == 0xFF && bytes.u8(bytes.size - 1) == 0xD9
        )
        var pos = 2
        var info: Info? = null
        while (pos < bytes.size) {
            assertEquals("JPEG segment marker expected at $pos", 0xFF, bytes.u8(pos))
            var marker = bytes.u8(pos + 1)
            while (marker == 0xFF) { pos++; marker = bytes.u8(pos + 1) }   // fill bytes
            if (marker in 0xD0..0xD7 || marker == 0x01) { pos += 2; continue }
            val length = bytes.u16(pos + 2)
            assertTrue("JPEG segment ${"%02X".format(marker)} at $pos truncated", pos + 2 + length <= bytes.size)
            // Start of frame (not DHT C4, JPG C8, DAC CC): height, width
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                info = Info(bytes.u16(pos + 7), bytes.u16(pos + 5))
            }
            if (marker == 0xDA) break                                   // start of scan: image data
            pos += 2 + length
        }
        assertTrue("no JPEG frame header (SOF)", info != null)
        return info!!
    }

    /** Decodes and fails on incomplete data instead of returning a partly grey image */
    fun decodeStrict(bytes: ByteArray): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE   // getPixel() needs a software bitmap
            decoder.setOnPartialImageListener { false }
        }

    fun readBytes(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    /** Full check of a saved file; returns the decoded image for pixel checks */
    fun assertComplete(context: Context, uri: Uri, format: SaveFormat, width: Int, height: Int): Bitmap {
        val bytes = readBytes(context, uri)
        val info = if (format == SaveFormat.PNG) checkPng(bytes) else checkJpeg(bytes)
        assertEquals("size in file header of $uri", Info(width, height), info)
        val bitmap = decodeStrict(bytes)
        assertEquals("decoded width", width, bitmap.width)
        assertEquals("decoded height", height, bitmap.height)
        return bitmap
    }

    fun assertColor(bitmap: Bitmap, x: Int, y: Int, expected: Int, tolerance: Int, what: String) {
        val actual = bitmap.getPixel(x, y)
        val diff = maxOf(
            abs(Color.red(actual) - Color.red(expected)),
            abs(Color.green(actual) - Color.green(expected)),
            abs(Color.blue(actual) - Color.blue(expected)),
        )
        assertTrue(
            "$what at ($x, $y): expected #%06X, got #%06X".format(expected and 0xFFFFFF, actual and 0xFFFFFF),
            diff <= tolerance
        )
    }

    /** Average brightness 0-255 over a grid of sample points */
    fun meanLuminance(bitmap: Bitmap): Double {
        var sum = 0.0
        var count = 0
        for (y in 0 until bitmap.height step maxOf(1, bitmap.height / 20))
            for (x in 0 until bitmap.width step maxOf(1, bitmap.width / 20)) {
                val c = bitmap.getPixel(x, y)
                sum += 0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)
                count++
            }
        return sum / count
    }
}
