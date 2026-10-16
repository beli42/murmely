package org.pixeldroid.media_editor.photoEdit.overlay

/**
 * Fonts for texts: the device's default font and fonts bundled in assets/fonts (see the README
 * there for sources and licences). Pure, no Android; loaded by TextTypefaces.
 *
 * @param file font file in assets/fonts, null for the default font
 * @param title the font's own name (not translated), null for the default font
 */
enum class TextFont(val file: String?, val title: String?) {
    DEFAULT(null, null),
    BEBAS_NEUE("BebasNeue-Regular.ttf", "Bebas Neue"),
    ANTON("Anton-Regular.ttf", "Anton"),
    PACIFICO("Pacifico-Regular.ttf", "Pacifico"),
    CAVEAT("Caveat.ttf", "Caveat"),
    PERMANENT_MARKER("PermanentMarker-Regular.ttf", "Permanent Marker"),
    PLAYFAIR_DISPLAY("PlayfairDisplay.ttf", "Playfair Display"),
    LOBSTER("Lobster-Regular.ttf", "Lobster");

    /** Path in the assets */
    val assetPath: String? get() = file?.let { "fonts/$it" }
}
