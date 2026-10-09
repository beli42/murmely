package org.pixeldroid.media_editor.photoEdit

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saving an image directly, without asking for a location, into either
 * - a folder chosen by the user with ACTION_OPEN_DOCUMENT_TREE ([folderUri], all Android versions), or
 * - a folder inside Pictures ([folderName], Android 10+, via MediaStore).
 *
 * Use [create] to make the new (empty) file, write the image to it, then call [finish]; on
 * failure call [discard] to remove the half-written file.
 */
class DirectSave(
    private val context: Context,
    private val folderName: String?,
    private val folderUri: Uri?,
) {
    /** Whether a direct save target is configured and usable on this Android version */
    val isAvailable: Boolean
        get() = folderUri != null ||
            (folderName != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

    /** File created by [create] that has not been finished or discarded yet */
    private var pending: Uri? = null

    /**
     * Creates a new empty PNG in the save folder. [chooseName] gets the file names already in
     * the folder and returns the name to use. Returns null if the folder is not accessible.
     */
    fun create(chooseName: (Set<String>) -> String): Uri? {
        val uri = folderUri?.let { createInTree(it, chooseName) }
            ?: if (folderUri == null && folderName != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                createInPictures(folderName, chooseName)
            else null
        pending = uri
        return uri
    }

    /** Marks the file written; a MediaStore entry becomes visible in the gallery */
    fun finish(uri: Uri) {
        if (uri != pending) return
        pending = null
        if (uri.authority == MediaStore.AUTHORITY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            context.contentResolver, folderDocUri, "image/png", chooseName(usedNames)
        )
    } catch (e: Exception) {
        null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createInPictures(folder: String, chooseName: (Set<String>) -> String): Uri? = try {
        val relativePath = "${Environment.DIRECTORY_PICTURES}/$folder/"
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        // Names already used in the folder (that this app can see; MediaStore additionally
        // avoids overwriting by renaming on insert)
        val usedNames = HashSet<String>()
        try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(relativePath),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) cursor.getString(0)?.let { usedNames.add(it) }
            }
        } catch (e: Exception) {
            // Continue without the list of existing names
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, chooseName(usedNames))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
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
            intent.getStringExtra(PhotoEditActivity.SAVE_TO_FOLDER_URI)?.let { Uri.parse(it) },
        )

        /**
         * For edited photos: "<base>-1.png", "<base>-2.png", ... first one not in [usedNames].
         * A trailing "-<n>" of [originalName] is dropped first, so editing "photo-1" again gives
         * "photo-2". Only 1-3 digit numbers without leading zero count as such a number, and names
         * ending in a date ("2026-10-10") are left alone.
         */
        fun numberedName(originalName: String, usedNames: Set<String>): String {
            val looksLikeDate = Regex("\\d{4}-\\d{2}-\\d{2}$").containsMatchIn(originalName)
            val base = (if (looksLikeDate) originalName
                else originalName.replace(Regex("-[1-9]\\d{0,2}$"), "")).ifBlank { "image" }
            var number = 1
            while ("$base-$number.png" in usedNames) number++
            return "$base-$number.png"
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
                uri.authority == MediaStore.AUTHORITY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
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

        /** Today as "2026-10-09" */
        fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

        /**
         * For collages: "<base>.png" if free, otherwise "<base>-1.png", "<base>-2.png", ...
         */
        fun nameWithOptionalNumber(base: String, usedNames: Set<String>): String {
            if ("$base.png" !in usedNames) return "$base.png"
            var number = 1
            while ("$base-$number.png" in usedNames) number++
            return "$base-$number.png"
        }
    }
}
