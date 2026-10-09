package eu.artectrex.bunny

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/**
 * Where edited images are saved. Either a folder inside Pictures (by name, Android 10+) or a
 * folder chosen by the user with the system folder picker (all Android versions).
 */
object SaveSettings {
    const val KEY_FOLDER_NAME = "save_folder_name"
    const val KEY_FOLDER_URI = "save_folder_uri"
    const val DEFAULT_FOLDER_NAME = "Bunny"

    /** Folder name inside Pictures, sanitised; used when no custom folder is chosen */
    fun folderName(context: Context): String =
        sanitize(prefs(context).getString(KEY_FOLDER_NAME, null)) ?: DEFAULT_FOLDER_NAME

    /** Folder chosen with the system folder picker, or null */
    fun folderUri(context: Context): Uri? =
        prefs(context).getString(KEY_FOLDER_URI, null)?.let { Uri.parse(it) }

    fun setFolderUri(context: Context, uri: Uri?) =
        prefs(context).edit { putString(KEY_FOLDER_URI, uri?.toString()) }

    /** Human readable description of the current save location, e.g. "Pictures/Bunny" */
    fun describe(context: Context): String {
        folderUri(context)?.let { return describeTreeUri(it) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Pictures/${folderName(context)}"
        else context.getString(R.string.save_location_ask)
    }

    /**
     * Turns a folder picked with ACTION_OPEN_DOCUMENT_TREE into something readable.
     * For the internal storage provider the document id is "primary:Some/Folder".
     */
    fun describeTreeUri(uri: Uri): String = try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val volume = docId.substringBefore(':')
        val path = docId.substringAfter(':', "")
        if (volume == "primary") path.ifEmpty { "/" } else "$volume: $path"
    } catch (e: Exception) {
        uri.toString()
    }

    /**
     * Allows nested folders like "Bunny/Edits", removes characters that are not allowed in
     * folder names and "." / ".." segments. Returns null if nothing usable is left.
     */
    fun sanitize(name: String?): String? = name
        ?.split('/')
        ?.map { it.trim().replace(Regex("[\\\\:*?\"<>|]"), "") }
        ?.filter { it.isNotEmpty() && it != "." && it != ".." }
        ?.joinToString("/")
        ?.ifEmpty { null }

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)
}
