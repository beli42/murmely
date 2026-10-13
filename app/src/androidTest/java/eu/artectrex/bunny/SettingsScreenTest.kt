package eu.artectrex.bunny

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.artectrex.bunny.settings.SettingsTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The settings screen (Compose): what it shows and what it stores */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val compose = createAndroidComposeRule<SettingsActivity>()

    // Settings are saved and reset before the screen is opened, and restored afterwards
    @get:Rule val rules: RuleChain = RuleChain.outerRule(PreferencesBackupRule(context)).around(compose)

    @Test fun showsTheDefaultLocation() {
        compose.onNodeWithTag(SettingsTags.LOCATION).assert(hasText("Pictures/Bunny", substring = true))
        compose.onNodeWithTag(SettingsTags.PICK_FOLDER).assertIsDisplayed()
    }

    @Test fun pngIsTheDefaultAndHasNoQuality() {
        compose.onNodeWithTag(SettingsTags.FORMAT_PNG).assertIsSelected()
        compose.onNodeWithTag(SettingsTags.FORMAT_JPEG).assertIsNotSelected()
        compose.onNodeWithTag(SettingsTags.JPEG_QUALITY).assertDoesNotExist()
    }

    @Test fun choosingJpgShowsQualityAndIsStored() {
        compose.onNodeWithTag(SettingsTags.FORMAT_JPEG).performClick()

        compose.onNodeWithTag(SettingsTags.FORMAT_JPEG).assertIsSelected()
        compose.onNodeWithTag(SettingsTags.JPEG_QUALITY)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(93f, 10f..100f, 0)
            ))
        assertEquals("jpeg", SaveSettings.format(context))

        compose.onNodeWithTag(SettingsTags.FORMAT_PNG).performClick()
        compose.onNodeWithTag(SettingsTags.JPEG_QUALITY).assertDoesNotExist()
        assertEquals("png", SaveSettings.format(context))
    }
}
