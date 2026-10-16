package org.pixeldroid.media_editor.photoEdit.overlay

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

/**
 * The typefaces of the [TextFont]s, loaded once from the assets. [init] must be called before
 * texts are drawn (the editor does it); until then, and if a font can't be loaded, the default
 * font is used.
 */
object TextTypefaces {

    @Volatile
    private var assets: AssetManager? = null
    private val loaded = ConcurrentHashMap<TextFont, Typeface>()

    fun init(context: Context) {
        assets = context.applicationContext.assets
    }

    fun typeface(font: TextFont): Typeface {
        val path = font.assetPath ?: return Typeface.DEFAULT
        val assets = assets ?: return Typeface.DEFAULT
        return loaded.getOrPut(font) {
            runCatching { Typeface.createFromAsset(assets, path) }.getOrDefault(Typeface.DEFAULT)
        }
    }
}
