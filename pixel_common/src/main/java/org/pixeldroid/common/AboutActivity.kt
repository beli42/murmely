package org.pixeldroid.common

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintProperties.MATCH_CONSTRAINT
import androidx.constraintlayout.widget.ConstraintProperties.WRAP_CONTENT
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
        //Optional, if null don't show release lines
        val releaseUrl: String? = intent.getStringExtra("releaseUrl")
        //Optional, if null don't show contribute lines
        val contributeForgeUrl: String? = intent.getStringExtra("contributeForgeUrl")

        // appImage is a name of a drawable, for example if the filename is 'mascot.xml' it would
        // be 'mascot'
        val appImage: String? = intent.getStringExtra("appImage")

        // Margins to apply to the drawable (could be negative to remove extra whitespace
        val appImageTopMargin: Int = intent.getIntExtra("appImageTopMargin", 0).dpToPx(this)
        val appImageBottomMargin: Int = intent.getIntExtra("appImageBottomMargin", 0).dpToPx(this)
        val appImageLeftMargin: Int = intent.getIntExtra("appImageLeftMargin", 0).dpToPx(this)
        val appImageRightMargin: Int = intent.getIntExtra("appImageRightMargin", 0).dpToPx(this)
        // Dimensions
        val appImageWidth: Int = intent.getIntExtra("appImageWidth", WRAP_CONTENT).let {
            if (it == MATCH_CONSTRAINT || it == WRAP_CONTENT) it else it.dpToPx(this)
        }
        val appImageHeight: Int = intent.getIntExtra("appImageHeight", WRAP_CONTENT).let {
            if (it == MATCH_CONSTRAINT || it == WRAP_CONTENT) it else it.dpToPx(this)
        }

        @SuppressLint("DiscouragedApi")
        val drawableID: Int = applicationContext.resources.getIdentifier(
            appImage, "drawable",
            application.packageName
        )

        val drawable = AppCompatResources.getDrawable(applicationContext, drawableID)
        binding.imageView.setImageDrawable(drawable)

        (binding.imageView.layoutParams as ConstraintLayout.LayoutParams).apply {
            this.width = appImageWidth
            this.height = appImageHeight
            // Bottom is used in aboutAppName top margin instead
            setMargins(appImageLeftMargin, appImageTopMargin, appImageRightMargin, 0)
        }

        (binding.aboutAppName.layoutParams as ConstraintLayout.LayoutParams).apply {
            setMargins(0, appImageBottomMargin, 0, 0)
        }

        binding.aboutAppName.text = appName
        binding.aboutAppDescription.text = aboutAppDescription
        binding.aboutWebsite.text = website
        releaseUrl?.let {
            binding.releaseText.isVisible = true
            binding.releaseUrl.isVisible = true
            binding.releaseUrl.text = it
        }
        contributeForgeUrl?.let {
            binding.contributeForgeText.isVisible = true
            binding.contributeForgeUrl.isVisible = true
            binding.contributeForgeUrl.text = it
        }

        setContentView(binding.root)
        setSupportActionBar(binding.topBar)

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