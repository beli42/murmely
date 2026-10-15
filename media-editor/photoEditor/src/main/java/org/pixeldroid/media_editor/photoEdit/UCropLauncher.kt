package org.pixeldroid.media_editor.photoEdit

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.yalantis.ucrop.UCrop
import java.io.File

/**
 * Cropping with uCrop in its own activity. Temporary: everything uCrop-related is in this one
 * file, so the planned own crop tool only has to replace it.
 *
 * Must be created while [activity] is being constructed (it registers for an activity result).
 *
 * @param onCropped the cropped image, as a new file in the cache
 */
class UCropLauncher(private val activity: ComponentActivity, private val onCropped: (Uri) -> Unit) {

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val cropped = result.data?.let { UCrop.getOutput(it) }
                if (cropped != null) onCropped(cropped) else error(null)
            }
            UCrop.RESULT_ERROR -> error(result.data?.let { UCrop.getError(it) })
        }
    }

    /** Crops [source] */
    fun launch(source: Uri) {
        val file = File.createTempFile("temp_crop_img", ".png", activity.cacheDir)

        // uCrop draws edge to edge since 2.2.11: the status and navigation bars are transparent,
        // only their icons are dark (light theme) or light (dark theme)
        val lightTheme = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES
        val options = UCrop.Options().apply {
            setStatusBarLight(lightTheme)
            setNavigationBarLight(lightTheme)
            setToolbarWidgetColor(activity.getColorFromAttr(R.attr.colorOnSurface))
            setToolbarColor(activity.getColorFromAttr(R.attr.colorSurface))
            setActiveControlsWidgetColor(activity.getColorFromAttr(R.attr.colorPrimary))
            setFreeStyleCropEnabled(true)
        }
        launcher.launch(UCrop.of(source, Uri.fromFile(file)).withOptions(options).getIntent(activity))
    }

    private fun error(cause: Throwable?) {
        if (cause != null) Toast.makeText(activity, "$cause", Toast.LENGTH_SHORT).show()
        else Toast.makeText(activity, R.string.crop_result_error, Toast.LENGTH_SHORT).show()
    }
}
