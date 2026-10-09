package eu.artectrex.bunny

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.pixeldroid.media_editor.photoEdit.PhotoEditActivity

/**
 * Where edited images are saved: a folder chosen by the user with the system folder picker,
 * or by default Pictures/Bunny.
 */
object SaveSettings {
    const val KEY_FOLDER_URI = "save_folder_uri"

    /** Folder inside Pictures used when no custom folder is chosen */
    const val FOLDER_NAME = "Bunny"

    /** Folder chosen with the system folder picker, or null */
    fun folderUri(context: Context): Uri? =
        prefs(context).getString(KEY_FOLDER_URI, null)?.let { Uri.parse(it) }

    fun setFolderUri(context: Context, uri: Uri?) =
        prefs(context).edit { putString(KEY_FOLDER_URI, uri?.toString()) }

    /** Human readable description of the current save location, e.g. "Pictures/Bunny" */
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

    fun jpegQuality(context: Context): Int =
        SaveLocationText.clampQuality(prefs(context).getInt(KEY_JPEG_QUALITY, DEFAULT_JPEG_QUALITY))

    /** Adds all save settings to an Intent for the photo editor or the collage */
    fun addTo(intent: android.content.Intent, context: Context): android.content.Intent = intent
        .putExtra(PhotoEditActivity.SAVE_TO_FOLDER, FOLDER_NAME)
        .putExtra(PhotoEditActivity.SAVE_TO_FOLDER_URI, folderUri(context)?.toString())
        .putExtra(PhotoEditActivity.SAVE_FORMAT, format(context))
        .putExtra(PhotoEditActivity.SAVE_JPEG_QUALITY, jpegQuality(context))

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)
}
