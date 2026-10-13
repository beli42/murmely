package eu.artectrex.bunny

import android.os.Bundle
import androidx.activity.compose.setContent
import eu.artectrex.bunny.settings.SettingsScreen
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.common.ThemedActivity

/** Settings, as a Compose screen (the window theme still comes from ThemedActivity) */
class SettingsActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BunnyTheme {
                SettingsScreen(onBack = ::finish)
            }
        }
    }
}
