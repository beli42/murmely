package org.pixeldroid.media_editor.photoEdit.templates

import org.pixeldroid.media_editor.photoEdit.Sliders
import org.pixeldroid.media_editor.photoEdit.frame.FrameSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings
import org.pixeldroid.media_editor.photoEdit.overlay.StickerItem
import org.pixeldroid.media_editor.photoEdit.overlay.TextItem

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
 * A saved look, named by the user: slider values, filters in order and a frame, and if wanted
 * stickers and texts. Applying it replaces sliders, filters and frame on the photo being edited
 * and adds its stickers and texts; the photo's own drawings, texts, stickers, crop and canvas stay.
 */
data class Template(
    val name: String,
    val sliders: Sliders = Sliders(),
    val filters: List<TemplateFilter> = emptyList(),
    val frame: FrameSettings? = null,
    /**
     * Stickers and texts to put on every photo (e.g. a watermark), positioned relative to the
     * photo; only when saved with them. Sticker images are copies in the app's own storage.
     */
    val stickers: List<StickerItem> = emptyList(),
    val texts: List<TextItem> = emptyList(),
)
