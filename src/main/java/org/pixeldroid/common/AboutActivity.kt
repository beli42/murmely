package org.pixeldroid.common

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.core.view.isVisible
import org.pixeldroid.common.databinding.ActivityAboutBinding

class AboutActivity: ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)

        val buildVersion: String = intent.getStringExtra("buildVersion")!!
        val appName: String = intent.getStringExtra("appName")!!

        val aboutAppDescription: String = intent.getStringExtra("aboutAppDescription")!!
        val website: String = intent.getStringExtra("website")!!
        //Optional, if null don't show translate lines
        val translatePlatformUrl: String? = intent.getStringExtra("translatePlatformUrl")
        //Optional, if null don't show contribute lines
        val contributeForgeUrl: String? = intent.getStringExtra("contributeForgeUrl")

        // appImage is a path to a drawable given in xml with @drawable/xyz, which is in the form
        // of a path such as "res/drawable-anydpi-v24/mascot.xml" from which we want "mascot"
        val appImage: String? = intent.getStringExtra("appImage")
            ?.split('/')?.last()
            ?.split('.')?.first()

        val drawableID: Int = resources.getIdentifier(
            appImage, "drawable",
            packageName
        )

        binding.imageView.setImageResource(drawableID)

        binding.aboutAppName.text = appName
        binding.aboutAppDescription.text = aboutAppDescription
        binding.aboutWebsite.text = website
        translatePlatformUrl?.let {
            binding.contributeTranslationsText.isVisible = true
            binding.contributeTranslationsUrl.isVisible = true
            binding.contributeTranslationsText.text = getString(R.string.help_translate, appName)
            binding.contributeTranslationsUrl.text = it
        }
        contributeForgeUrl?.let {
            binding.contributeForgeText.isVisible = true
            binding.contributeForgeUrl.isVisible = true
            binding.contributeForgeUrl.text = it
        }

        setContentView(binding.root)
        setSupportActionBar(binding.topBar)

        //TODO fix up button not working
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about_app, appName)

        binding.aboutVersionNumber.text = buildVersion
        binding.licensesButton.setOnClickListener {
            val intent = Intent(this, LicenseActivity::class.java)
            startActivity(intent)
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Handle up arrow manually,
                // since "up" doesn't seem to mean much within library activity
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }
}