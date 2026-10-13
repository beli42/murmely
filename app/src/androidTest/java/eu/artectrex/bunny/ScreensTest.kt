package eu.artectrex.bunny

import android.content.Intent
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.artectrex.bunny.start.StartTags
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI

/** Start screen (Compose): tiles, exit, menu and the saved message */
@RunWith(AndroidJUnit4::class)
class ScreensTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val savedImage = SavedTestImage(context)
    private val compose = createAndroidComposeRule<MainActivity>()

    // Settings saved/restored and the test image created before the app starts
    @get:Rule val rules: RuleChain =
        RuleChain.outerRule(PreferencesBackupRule(context)).around(savedImage).around(compose)

    @Test fun startScreenShowsBothTiles() {
        val screenHeight = compose.onRoot().fetchSemanticsNode().size.height
        for (tag in listOf(StartTags.EDIT_TILE, StartTags.COLLAGE_TILE)) {
            val tile = compose.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction()
            assertTrue("$tag is large", tile.fetchSemanticsNode().size.height > screenHeight / 5)
        }
    }

    @Test fun exitIconClosesTheAppWithoutAsking() {
        compose.onNodeWithTag(StartTags.EXIT).assertIsDisplayed().performClick()
        assertTrue("app is closing", compose.activity.isFinishing)
    }

    @Test fun exitInTheMenuClosesTheAppWithoutAsking() {
        compose.onNodeWithTag(StartTags.MORE).performClick()
        compose.onNodeWithTag(StartTags.SETTINGS).assertIsDisplayed()
        compose.onNodeWithTag(StartTags.ABOUT).assertIsDisplayed()
        compose.onNodeWithTag(StartTags.EXIT_MENU).assertIsDisplayed().performClick()
        assertTrue("app is closing", compose.activity.isFinishing)
    }

    @Test fun savedMessageAppearsOnTheStartScreen() {
        val model = ViewModelProvider(compose.activity, EditViewModel.Factory)[EditViewModel::class.java]
        // What happens when a collage comes back saved
        compose.runOnUiThread {
            model.launchedCollage()
            model.editResult(Intent().putExtra(PICTURE_URI, savedImage.uri.toString()))
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(savedImage.location, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertNull("message is shown only once", model.uiState.value.savedMessage)
    }
}
