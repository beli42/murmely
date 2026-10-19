package eu.artectrex.bunny

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.photoEdit.PhotoEditActivity

@RunWith(AndroidJUnit4::class)
class SaveSettingsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule val preferences = PreferencesBackupRule(context)

    private val prefs get() = SaveSettings.prefs(context)
    private val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APictures%2FUrlaub")

    @Test fun defaults() {
        assertNull(SaveSettings.folderUri(context))
        assertEquals("png", SaveSettings.format(context))
        assertEquals(93, SaveSettings.jpegQuality(context))
        assertEquals("Pictures/Murmely", SaveSettings.describe(context))
    }

    @Test fun chosenFolderIsDescribed() {
        SaveSettings.setFolderUri(context, tree)
        assertEquals(tree, SaveSettings.folderUri(context))
        assertEquals("Pictures/Urlaub", SaveSettings.describe(context))
        SaveSettings.setFolderUri(context, null)
        assertEquals("Pictures/Murmely", SaveSettings.describe(context))
    }

    @Test fun qualityIsClamped() {
        prefs.edit().putInt(SaveSettings.KEY_JPEG_QUALITY, 3).commit()
        assertEquals(10, SaveSettings.jpegQuality(context))
        prefs.edit().putInt(SaveSettings.KEY_JPEG_QUALITY, 500).commit()
        assertEquals(100, SaveSettings.jpegQuality(context))
    }

    /** The editors get exactly the settings */
    @Test fun settingsArePassedToTheEditors() {
        prefs.edit().putString(SaveSettings.KEY_FORMAT, "jpeg").putInt(SaveSettings.KEY_JPEG_QUALITY, 80).commit()
        SaveSettings.setFolderUri(context, tree)

        val intent = SaveSettings.addTo(Intent(), context)
        assertEquals("Murmely", intent.getStringExtra(PhotoEditActivity.SAVE_TO_FOLDER))
        assertEquals(tree.toString(), intent.getStringExtra(PhotoEditActivity.SAVE_TO_FOLDER_URI))
        assertEquals("jpeg", intent.getStringExtra(PhotoEditActivity.SAVE_FORMAT))
        assertEquals(80, intent.getIntExtra(PhotoEditActivity.SAVE_JPEG_QUALITY, -1))
    }

    @Test fun withoutChosenFolderNoUriIsPassed() {
        val intent = SaveSettings.addTo(Intent(), context)
        assertNull(intent.getStringExtra(PhotoEditActivity.SAVE_TO_FOLDER_URI))
        assertEquals("png", intent.getStringExtra(PhotoEditActivity.SAVE_FORMAT))
    }
}
