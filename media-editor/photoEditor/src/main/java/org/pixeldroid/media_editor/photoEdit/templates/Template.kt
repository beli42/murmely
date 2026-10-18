package org.pixeldroid.media_editor.photoEdit.templates

import org.pixeldroid.media_editor.photoEdit.Sliders
import org.pixeldroid.media_editor.photoEdit.frame.FrameSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings

/**
 * A filter in a template: a built-in one by its [key] (see BuiltInFilters.KEYS), or an own one
 * by its [customName] and shader [source] (kept here, so the template still works after the own
 * filter was deleted); with its [settings]
 */
data class TemplateFilter(
    val key: String? = null,
    val customName: String? = null,
    val source: String? = null,
    val settings: FilterSettings = FilterSettings.DEFAULT,
)

/**
 * A saved look, named by the user: slider values, filters in order and a frame. Applying it
 * replaces these on the photo being edited; drawings, texts, stickers, crop and canvas belong
 * to the photo and stay.
 */
data class Template(
    val name: String,
    val sliders: Sliders = Sliders(),
    val filters: List<TemplateFilter> = emptyList(),
    val frame: FrameSettings? = null,
)
