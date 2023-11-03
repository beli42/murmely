package org.pixeldroid.common

import android.content.Context
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

open class ThemedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set theme when we chose one
        themeNoActionBar()?.let { setTheme(it) }
        super.onCreate(savedInstanceState)
    }
}

@StyleRes
fun Context.themeNoActionBar(): Int? {
    return when (PreferenceManager.getDefaultSharedPreferences(this).getInt("themeColor", 0)) {
        // No theme was chosen: the user wants to use the system dynamic color (from wallpaper for example)
        -1 -> null
        1 -> R.style.AppTheme2_NoActionBar
        2 -> R.style.AppTheme3_NoActionBar
        3 -> R.style.AppTheme4_NoActionBar
        else -> R.style.AppTheme5_NoActionBar
    }
}