package eu.artectrex.bunny

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import eu.artectrex.bunny.start.BunnyApp
import eu.artectrex.bunny.ui.BunnyTheme
import org.pixeldroid.common.AboutActivity
import org.pixeldroid.common.ThemedActivity

/** Start of the app; the screens are in [BunnyApp] (Compose) */
class MainActivity : ThemedActivity() {

    private val model: EditViewModel by viewModels { EditViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars; the Compose screens keep their content clear of them
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        setContent {
            BunnyTheme {
                BunnyApp(
                    viewModel = model,
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onAbout = ::openAbout,
                    onExit = ::exit,
                )
            }
        }
    }

    private fun openAbout() {
        val intent = Intent(this, AboutActivity::class.java)
            .putExtra("buildVersion", getString(R.string.versionName))
            .putExtra("appName", getString(R.string.app_name))
            .putExtra("aboutAppDescription", getString(R.string.license_info))
            .putExtra("appImage", "mascot")
            .putExtra("appImageTopMargin", 10)
            .putExtra("website", getString(R.string.project_website))
            .putExtra("translatePlatformUrl", "https://weblate.pixeldroid.org")
            .putExtra("contributeForgeUrl", "https://gitlab.shinice.net/pixeldroid/bunny")
        startActivity(intent)
    }

    /**
     * Closes the app right away: stops a running video conversion, closes the screen and
     * removes Bunny from the recent apps
     */
    private fun exit() {
        model.cancelVideoProcessing()
        finishAndRemoveTask()
    }
}
