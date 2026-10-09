package org.pixeldroid.media_editor.photoEdit

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Source images for the tests, written to the app's cache */
object TestImages {

    private fun file(context: Context, name: String) =
        File(context.cacheDir, "test-images").apply { mkdirs() }.let { File(it, name) }

    fun write(context: Context, name: String, bitmap: Bitmap): Uri {
        val f = file(context, name)
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(f)
    }

    fun solid(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    /** Smooth colour gradient: lossless formats must reproduce it exactly */
    fun gradient(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val row = IntArray(width)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    row[x] = Color.rgb(x * 255 / width, y * 255 / height, (x + y) * 255 / (width + height))
                }
                setPixels(row, 0, width, 0, y, width, 1)
            }
        }

    /** A valid image file, cut off after [fraction] of its bytes */
    fun truncated(context: Context, name: String, bitmap: Bitmap, fraction: Double): Uri {
        val full = file(context, "full-$name")
        full.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val bytes = full.readBytes()
        val f = file(context, name)
        f.writeBytes(bytes.copyOf((bytes.size * fraction).toInt()))
        return Uri.fromFile(f)
    }
}

/** A unique folder in Pictures for one test; [deleteAll] removes everything the test saved */
class TestFolder(private val context: Context) {

    val name = "BunnyTest-${System.currentTimeMillis()}"
    private val relativePath = "${Environment.DIRECTORY_PICTURES}/$name/"
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** All files in the folder (display name to Uri), including ones still being written */
    fun files(): Map<String, Uri> {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.contentResolver.query(collection, projection, Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(relativePath))
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }, null)
        } else {
            @Suppress("DEPRECATION")
            context.contentResolver.query(
                MediaStore.setIncludePending(collection), projection, selection, arrayOf(relativePath), null
            )
        }
        val result = LinkedHashMap<String, Uri>()
        cursor?.use {
            while (it.moveToNext()) {
                result[it.getString(1)] = Uri.withAppendedPath(collection, it.getLong(0).toString())
            }
        }
        return result
    }

    fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)!!
            .use { it.moveToFirst(); it.getString(0) }

    fun deleteAll() = files().values.forEach { context.contentResolver.delete(it, null, null) }
}
