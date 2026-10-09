package eu.artectrex.bunny

/** Text and value helpers for the save settings. Pure functions, no Android dependencies. */
object SaveLocationText {

    /**
     * Readable form of a document id from the system folder picker: the internal storage
     * provider uses "primary:Some/Folder", other volumes (SD cards) "1234-ABCD:Some/Folder".
     */
    fun describeDocumentId(docId: String): String {
        val volume = docId.substringBefore(':')
        val path = docId.substringAfter(':', "")
        return if (volume == "primary") path.ifEmpty { "/" } else "$volume: $path"
    }

    /** JPG quality as used for saving: 10-100 */
    fun clampQuality(quality: Int): Int = quality.coerceIn(10, 100)

    /** Only content:// Uris are accepted from other apps (see [EditViewModel]) */
    fun isAcceptedSharedScheme(scheme: String?): Boolean = scheme == "content"
}
