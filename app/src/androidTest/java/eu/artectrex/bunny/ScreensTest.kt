package eu.artectrex.bunny

import android.app.Application
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI

/**
 * Start screen and settings page. No Espresso: actions are triggered directly
 * (Espresso's touch injection doesn't work on newer Android versions).
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule val preferences = PreferencesBackupRule(context)
    @get:Rule val savedImage = SavedTestImage(context)

    @Test fun startScreenShowsBothTiles() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (id in listOf(R.id.button_first, R.id.button_collage)) {
                    val tile = activity.findViewById<View>(id)
                    assertNotNull(tile)
                    assertTrue("tile visible", tile.isShown)
                    assertTrue("tile clickable", tile.isClickable)
                    assertTrue("tile is large", tile.height > tile.rootView.height / 5)
                }
            }
        }
    }

    /** Exit as toolbar icon and as menu entry: both close the app without asking */
    @Test fun exitClosesTheAppWithoutAsking() {
        for (id in listOf(R.id.action_exit_icon, R.id.action_exit)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.top_bar)
                    val item = toolbar.menu.findItem(id)
                    assertNotNull("menu item $id exists", item)
                    assertTrue(item.isVisible)
                    if (id == R.id.action_exit_icon) {
                        // Shown as an icon in the toolbar, not hidden in the three-dot menu
                        assertNotNull("exit icon visible in the toolbar", toolbar.findViewById<View>(id))
                    }
                    activity.onOptionsItemSelected(item)
                    // Closing has started; removing the task finishes asynchronously
                    assertTrue("app is closing", activity.isFinishing)
                }
            }
        }
    }

    @Test fun savedMessageAppearsOnTheStartScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: EditViewModel? = null
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity, EditViewModelFactory(activity.application as Application))[EditViewModel::class.java]
                // What happens when a collage comes back saved
                model!!.launchedCollage()
                model!!.editResult(Intent().putExtra(PICTURE_URI, savedImage.uri.toString()))
            }
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                val text = activity.window.decorView
                    .findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                assertNotNull("no message shown", text)
                assertTrue("message: ${text.text}", text.text.contains(savedImage.location))
                assertNull("message is shown only once", model!!.uiState.value.savedMessage)
            }
        }
    }

    @Test fun settingsShowLocationAndQualityOnlyForJpg() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager
                    .findFragmentById(R.id.settings_container) as PreferenceFragmentCompat
                assertEquals("Pictures/Bunny", fragment.findPreference<androidx.preference.Preference>("save_location_current")!!.summary)

                val format = fragment.findPreference<ListPreference>(SaveSettings.KEY_FORMAT)!!
                val quality = fragment.findPreference<SeekBarPreference>(SaveSettings.KEY_JPEG_QUALITY)!!
                assertEquals("png", format.value)
                assertFalse("quality hidden for PNG", quality.isVisible)
                assertEquals(93, quality.value)

                // Choose JPG like the user would
                if (format.callChangeListener("jpeg")) format.value = "jpeg"
                assertTrue("quality shown for JPG", quality.isVisible)
                assertEquals("jpeg", SaveSettings.format(activity))

                if (format.callChangeListener("png")) format.value = "png"
                assertFalse(quality.isVisible)
            }
        }
    }
}
