package eu.artectrex.bunny

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import eu.artectrex.bunny.databinding.ActivitySettingsBinding
import org.pixeldroid.common.ThemedActivity

class SettingsActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val resolver = requireContext().contentResolver
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            // Keep access to the folder after restarts
            resolver.takePersistableUriPermission(uri, flags)
            // Give up access to a previously chosen folder
            SaveSettings.folderUri(requireContext())?.let { old ->
                if (old != uri) releaseFolder(old)
            }
            SaveSettings.setFolderUri(requireContext(), uri)
            updateSummaries()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<Preference>("save_folder_pick")?.setOnPreferenceClickListener {
                // Start the picker in the current folder or in Pictures
                val initial = SaveSettings.folderUri(requireContext())
                    ?: DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents", "primary:Pictures"
                    )
                pickFolder.launch(initial)
                true
            }

            updateSummaries()
        }

        private fun releaseFolder(uri: Uri) {
            try {
                requireContext().contentResolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Permission was already gone
            }
        }

        private fun updateSummaries() {
            findPreference<Preference>("save_location_current")?.summary =
                SaveSettings.describe(requireContext())
        }
    }
}
