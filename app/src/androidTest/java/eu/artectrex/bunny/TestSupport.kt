package eu.artectrex.bunny

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import org.junit.rules.ExternalResource

/**
 * App tests run inside the real app, so they would change the user's settings. This rule saves
 * all settings before each test and restores them exactly afterwards.
 */
class PreferencesBackupRule(private val context: Context) : ExternalResource() {
    private var saved: Map<String, *> = emptyMap<String, Any>()
    private val prefs get() = SaveSettings.prefs(context)

    override fun before() {
        saved = HashMap(prefs.all)
        prefs.edit().clear().commit()                    // every test starts with default settings
    }

    override fun after() {
        val editor = prefs.edit().clear()
        for ((key, value) in saved) when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
        editor.commit()
    }
}

/** A real PNG in a unique folder in Pictures, as the editors would have saved it */
class SavedTestImage(private val context: Context) : ExternalResource() {
    val folder = "BunnyAppTest-${System.currentTimeMillis()}"
    lateinit var uri: Uri
        private set
    val displayName = "test-image-1.png"

    override fun before() {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$folder/")
        }
        uri = context.contentResolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
        )!!
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    override fun after() {
        context.contentResolver.delete(uri, null, null)
    }

    /** "Pictures/<folder>/test-image-1.png" */
    val location get() = "${Environment.DIRECTORY_PICTURES}/$folder/$displayName"
}
