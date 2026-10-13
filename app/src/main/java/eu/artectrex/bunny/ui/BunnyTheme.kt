package eu.artectrex.bunny.ui

import android.content.Context
import android.util.TypedValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.R as MaterialR

/**
 * Material 3 theme for Compose screens. The colours are taken from the activity's XML theme, so
 * Compose screens look like the rest of the app while it is moved to Compose screen by screen.
 */
@Composable
fun BunnyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colorScheme = remember(context, dark) {
        colorSchemeFromXmlTheme(context, if (dark) darkColorScheme() else lightColorScheme())
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

private fun colorSchemeFromXmlTheme(context: Context, fallback: ColorScheme): ColorScheme {
    val value = TypedValue()
    fun color(attr: Int, default: Color): Color =
        if (context.theme.resolveAttribute(attr, value, true) &&
            value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT
        ) Color(value.data) else default

    return fallback.copy(
        primary = color(MaterialR.attr.colorPrimary, fallback.primary),
        onPrimary = color(MaterialR.attr.colorOnPrimary, fallback.onPrimary),
        primaryContainer = color(MaterialR.attr.colorPrimaryContainer, fallback.primaryContainer),
        onPrimaryContainer = color(MaterialR.attr.colorOnPrimaryContainer, fallback.onPrimaryContainer),
        secondary = color(MaterialR.attr.colorSecondary, fallback.secondary),
        onSecondary = color(MaterialR.attr.colorOnSecondary, fallback.onSecondary),
        secondaryContainer = color(MaterialR.attr.colorSecondaryContainer, fallback.secondaryContainer),
        onSecondaryContainer = color(MaterialR.attr.colorOnSecondaryContainer, fallback.onSecondaryContainer),
        background = color(android.R.attr.colorBackground, fallback.background),
        onBackground = color(MaterialR.attr.colorOnBackground, fallback.onBackground),
        surface = color(MaterialR.attr.colorSurface, fallback.surface),
        onSurface = color(MaterialR.attr.colorOnSurface, fallback.onSurface),
        surfaceVariant = color(MaterialR.attr.colorSurfaceVariant, fallback.surfaceVariant),
        onSurfaceVariant = color(MaterialR.attr.colorOnSurfaceVariant, fallback.onSurfaceVariant),
        outline = color(MaterialR.attr.colorOutline, fallback.outline),
        error = color(MaterialR.attr.colorError, fallback.error),
        onError = color(MaterialR.attr.colorOnError, fallback.onError),
    )
}
