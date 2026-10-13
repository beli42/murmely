package org.pixeldroid.media_editor.photoEdit

import android.content.ContentResolver
import androidx.core.net.toUri
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns

/**
 * Saving an image directly, without asking for a location, into either
 * - a folder chosen by the user with ACTION_OPEN_DOCUMENT_TREE ([folderUri]), or
 * - a folder inside Pictures ([folderName], via MediaStore).
 *
 * Use [create] to make the new (empty) file, write the image to it, then call [finish]; on
 * failure call [discard] to remove the half-written file.
 */
class DirectSave(
    private val context: Context,
    private val folderName: String?,
    private val folderUri: Uri?,
    /** File format for saved images, also used for "save as" */
    val format: SaveFormat = SaveFormat.PNG,
    /** JPEG quality 0-100 (ignored for PNG) */
    val quality: Int = SaveFormat.DEFAULT_JPEG_QUALITY,
) {
    /** Whether a direct save target is configured */
    val isAvailable: Boolean
        get() = folderUri != null || folderName != null

    /** File created by [create] that has not been finished or discarded yet */
    private var pending: Uri? = null

    /**
     * Creates a new empty image file (in [format]) in the save folder. [chooseName] gets the file names already in
     * the folder and returns the name to use. Returns null if the folder is not accessible.
     */
    fun create(chooseName: (Set<String>) -> String): Uri? {
        val uri = if (folderUri != null) createInTree(folderUri, chooseName)
            else folderName?.let { createInPictures(it, chooseName) }
        pending = uri
        return uri
    }

    /** Marks the file written; a MediaStore entry becomes visible in the gallery */
    fun finish(uri: Uri) {
        if (uri != pending) return
        pending = null
        if (uri.authority == MediaStore.AUTHORITY) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    /** Removes the file created by [create] after an error */
    fun discard() {
        val uri = pending ?: return
        pending = null
        try {
            if (uri.authority == MediaStore.AUTHORITY) context.contentResolver.delete(uri, null, null)
            else DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            // Nothing more we can do
        }
    }

    private fun createInTree(treeUri: Uri, chooseName: (Set<String>) -> String): Uri? = try {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val folderDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

        val usedNames = HashSet<String>()
        context.contentResolver.query(
            childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let { usedNames.add(it) }
        }
        DocumentsContract.createDocument(
            context.contentResolver, folderDocUri, format.mimeType, chooseName(usedNames)
        )
    } catch (e: Exception) {
        null
    }

    private fun createInPictures(folder: String, chooseName: (Set<String>) -> String): Uri? = try {
        val relativePath = "${Environment.DIRECTORY_PICTURES}/$folder/"
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        // Names already used in the folder (that this app can see; MediaStore additionally
        // avoids overwriting by renaming on insert)
        val usedNames = HashSet<String>()
        try {
            // Include files that are still being written (IS_PENDING); they are hidden by default,
            // and two saves in quick succession would otherwise pick the same name
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
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
            cursor?.use { cursor ->
                while (cursor.moveToNext()) cursor.getString(0)?.let { usedNames.add(it) }
            }
        } catch (e: Exception) {
            // Continue without the list of existing names
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, chooseName(usedNames))
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        context.contentResolver.insert(collection, values)
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Reads the save target from the extras of the Intent that started an editor */
        fun fromIntent(context: Context, intent: Intent) = DirectSave(
            context,
            intent.getStringExtra(PhotoEditActivity.SAVE_TO_FOLDER),
            intent.getStringExtra(PhotoEditActivity.SAVE_TO_FOLDER_URI)?.toUri(),
            SaveFormat.fromName(intent.getStringExtra(PhotoEditActivity.SAVE_FORMAT)),
            intent.getIntExtra(PhotoEditActivity.SAVE_JPEG_QUALITY, SaveFormat.DEFAULT_JPEG_QUALITY)
                .coerceIn(1, 100),
        )

        /**
         * Size of a saved file in bytes, or null if unknown. Reads the file itself, because the
         * size column of a just-written MediaStore entry may not be updated yet.
         */
        fun fileSize(context: Context, uri: Uri): Long? = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                ?.takeIf { it >= 0 }
        } catch (e: Exception) {
            null
        }

        /**
         * Human readable location of a saved file, e.g. "Pictures/Bunny/collage-2026-10-09.png".
         * Returns null if it can't be determined.
         */
        fun describe(context: Context, uri: Uri): String? = try {
            when {
                // Folder chosen with the folder picker, internal storage: "primary:Path/name.png"
                uri.authority == "com.android.externalstorage.documents" ->
                    DocumentsContract.getDocumentId(uri).substringAfter(':')
                // MediaStore entry: folder + name
                uri.authority == MediaStore.AUTHORITY ->
                    context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME),
                        null, null, null
                    )?.use { c -> if (c.moveToFirst()) "${c.getString(0) ?: ""}${c.getString(1)}" else null }
                // Anything else: just the file name
                else -> context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }
        } catch (e: Exception) {
            null
        }

    }
}
