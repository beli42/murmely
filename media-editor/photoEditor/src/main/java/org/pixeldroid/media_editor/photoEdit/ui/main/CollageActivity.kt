package org.pixeldroid.media_editor.photoEdit.ui.main

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.appcompat.app.AppCompatActivity
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.collage.CollageApp
import org.pixeldroid.media_editor.photoEdit.collage.CollageViewModel
import org.pixeldroid.media_editor.photoEdit.sendBackImage
import org.pixeldroid.media_editor.photoEdit.LocalLandscapePhotoShare
import org.pixeldroid.media_editor.photoEdit.landscapePhotoShare

/**
 * Collage editor (Compose). Started with the save settings as extras (see PhotoEditActivity);
 * returns the saved image like the photo editor does.
 */
class CollageActivity : AppCompatActivity() {

    private val viewModel: CollageViewModel by viewModels { CollageViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BunnyTheme {
                CompositionLocalProvider(LocalLandscapePhotoShare provides landscapePhotoShare(intent)) {
                    CollageApp(
                        viewModel = viewModel,
                        onSaved = { uri -> sendBackImage(uri.toString(), null) },
                        onClose = ::finish,
                    )
                }
            }
        }
    }
}
