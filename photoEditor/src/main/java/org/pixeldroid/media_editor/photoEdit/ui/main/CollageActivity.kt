package org.pixeldroid.media_editor.photoEdit.ui.main

import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.databinding.ActivityCollageBinding
import org.pixeldroid.media_editor.photoEdit.ui.main.ui.main.MainViewModel

class CollageActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityCollageBinding

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        viewModel.gotImage(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCollageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // With a FragmentContainerView the NavController must be taken from the NavHostFragment:
        // findNavController(id) crashes in onCreate because the view isn't set up yet.
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_collage) as NavHostFragment
        navController = navHostFragment.navController

        // No top-level destinations: the back arrow is shown on every screen, including the
        // collage format selection (start destination), where it closes the collage.
        appBarConfiguration = AppBarConfiguration(
            topLevelDestinationIds = emptySet(),
            fallbackOnNavigateUpListener = {
                finish()
                true
            }
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.clickedImage.collect {
                    if (it == null) return@collect
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}