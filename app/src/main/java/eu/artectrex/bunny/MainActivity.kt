package eu.artectrex.bunny

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import eu.artectrex.bunny.databinding.ActivityMainBinding
import org.pixeldroid.common.AboutActivity
import org.pixeldroid.common.ThemedActivity

class MainActivity : ThemedActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var model: EditViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        val _model: EditViewModel by viewModels {
            EditViewModelFactory(
                application,
            )
        }
        model = _model

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_about -> {
                val intent = Intent(this, AboutActivity::class.java)
                intent.putExtra("buildVersion", getString(R.string.versionName))
                intent.putExtra("appName", getString(R.string.app_name))
                intent.putExtra("aboutAppDescription", getString(R.string.license_info))
                intent.putExtra("appImage", "mascot")
                intent.putExtra("appImageTopMargin", 10)
                intent.putExtra("website", getString(R.string.project_website))
                intent.putExtra("translatePlatformUrl", "https://weblate.pixeldroid.org")
                intent.putExtra("contributeForgeUrl", "https://gitlab.shinice.net/pixeldroid/bunny")
                startActivity(intent)
                true
            }
            android.R.id.home -> {
                model.goToFirstFragment()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}