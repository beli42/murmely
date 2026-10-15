package org.pixeldroid.media_editor.photoEdit.overlay

/**
 * The built-in stickers: Noto Emoji images in assets/stickers/noto (see the README there for
 * source and licence), named by their Unicode code points. Pure, no Android.
 */
object StickerCatalog {

    /** Folder of the images in the assets */
    const val FOLDER = "stickers/noto"

    enum class Category(val codes: List<String>) {
        FACES(listOf("1f600", "1f602", "1f60d", "1f60e", "1f618", "1f609", "1f914", "1f973", "1f62d", "1f631", "1f621", "1f92f", "1f634", "1f917")),
        HEARTS(listOf("2764", "1f9e1", "1f49b", "1f49a", "1f499", "1f49c", "1f496", "2b50", "2728", "1f4af", "1f525", "1f44d", "1f44f", "1f64c")),
        ANIMALS(listOf("1f430", "1f407", "1f436", "1f431", "1f98a", "1f43b", "1f43c", "1f428", "1f981", "1f438", "1f427", "1f989", "1f98b", "1f984")),
        FOOD(listOf("1f355", "1f354", "1f369", "1f370", "1f366", "1f353", "1f951", "1f349", "2615", "1f37a", "1f377", "1f36d", "1f34e", "1f950")),
        PARTY(listOf("1f389", "1f388", "1f381", "1f382", "1f38a", "1f3c6", "1f451", "1f453", "1f3b5", "1f4f8", "1f680", "1f3a8", "1f48e", "1f387")),
        NATURE(listOf("2600", "1f308", "2601", "26a1", "2744", "1f319", "1f30d", "1f337", "1f33b", "1f338", "1f335", "1f343", "1f340", "1f30a")),
    }

    /** Path of a sticker in the assets */
    fun assetPath(code: String) = "$FOLDER/$code.webp"

    /** Uri of a sticker, as kept in [StickerItem.uri] (loaded like any other image uri) */
    fun uri(code: String) = "file:///android_asset/${assetPath(code)}"

    val all: List<String> get() = Category.entries.flatMap { it.codes }
}
