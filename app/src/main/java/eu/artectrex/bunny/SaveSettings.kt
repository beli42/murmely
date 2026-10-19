package eu.artectrex.bunny

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.net.toUri
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import org.pixeldroid.media_editor.photoEdit.PhotoEditActivity

/**
 * Where edited images are saved: a folder chosen by the user with the system folder picker,
 * or by default Pictures/Murmely.
 */
object SaveSettings {
    const val KEY_FOLDER_URI = "save_folder_uri"

    /** Folder inside Pictures used when no custom folder is chosen */
    const val FOLDER_NAME = "Murmely"

    /** Folder chosen with the system folder picker, or null */
    fun folderUri(context: Context): Uri? =
        prefs(context).getString(KEY_FOLDER_URI, null)?.toUri()

    fun setFolderUri(context: Context, uri: Uri?) =
        prefs(context).edit { putString(KEY_FOLDER_URI, uri?.toString()) }

    /**
     * Uses a folder picked with the system folder picker: keeps write access to it across
     * restarts and gives up access to the previously chosen folder.
     */
    fun chooseFolder(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        folderUri(context)?.let { old -> if (old != uri) releaseFolder(context, old) }
        setFolderUri(context, uri)
    }

    private fun releaseFolder(context: Context, uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Permission was already gone
        }
    }

    /** Where the folder picker opens: the chosen folder, otherwise Pictures */
    fun pickerStartUri(context: Context): Uri = folderUri(context)
        ?: DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Pictures")

    /** Human readable description of the current save location, e.g. "Pictures/Murmely" */
    fun describe(context: Context): String {
        folderUri(context)?.let { return describeTreeUri(it) }
        return "Pictures/$FOLDER_NAME"
    }

    /**
     * Turns a folder picked with ACTION_OPEN_DOCUMENT_TREE into something readable.
     * For the internal storage provider the document id is "primary:Some/Folder".
     */
    fun describeTreeUri(uri: Uri): String = try {
        SaveLocationText.describeDocumentId(DocumentsContract.getTreeDocumentId(uri))
    } catch (e: Exception) {
        uri.toString()
    }

    const val KEY_FORMAT = "save_format"
    const val KEY_JPEG_QUALITY = "save_jpeg_quality"
    const val DEFAULT_JPEG_QUALITY = 93

    /** "png" or "jpeg", as understood by the media editor */
    fun format(context: Context): String = prefs(context).getString(KEY_FORMAT, "png") ?: "png"

    fun setFormat(context: Context, format: String) = prefs(context).edit { putString(KEY_FORMAT, format) }

    fun jpegQuality(context: Context): Int =
        SaveLocationText.clampQuality(prefs(context).getInt(KEY_JPEG_QUALITY, DEFAULT_JPEG_QUALITY))

    fun setJpegQuality(context: Context, quality: Int) =
        prefs(context).edit { putInt(KEY_JPEG_QUALITY, SaveLocationText.clampQuality(quality)) }

    /** Adds all save settings to an Intent for the photo editor or the collage */
    fun addTo(intent: Intent, context: Context): Intent = intent
        .putExtra(PhotoEditActivity.SAVE_TO_FOLDER, FOLDER_NAME)
        .putExtra(PhotoEditActivity.SAVE_TO_FOLDER_URI, folderUri(context)?.toString())
        .putExtra(PhotoEditActivity.SAVE_FORMAT, format(context))
        .putExtra(PhotoEditActivity.SAVE_JPEG_QUALITY, jpegQuality(context))

    /**
     * The settings file. Same name as androidx.preference's default preferences, which were used
     * before, so existing settings are kept.
     */
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
}
