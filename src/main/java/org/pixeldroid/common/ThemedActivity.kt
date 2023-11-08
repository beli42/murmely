package org.pixeldroid.common

import android.content.Context
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

open class ThemedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set theme when we chose one
        theme()?.let { setTheme(it) }
        super.onCreate(savedInstanceState)
    }
}

@StyleRes
fun Context.theme(): Int? {
    return when (PreferenceManager.getDefaultSharedPreferences(this).getInt("themeColor", 0)) {
        // No theme was chosen: the user wants to use the system dynamic color (from wallpaper for example)
        -1 -> null
        1 -> R.style.AppTheme2
        2 -> R.style.AppTheme3
        3 -> R.style.AppTheme4
        else -> R.style.AppTheme5
    }
}