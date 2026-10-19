package org.pixeldroid.media_editor.photoEdit

import androidx.annotation.StringRes

/** Tabs below the photo in the editor */
enum class EditorTab(@StringRes val label: Int) {
    FILTERS(R.string.tab_filters),
    SLIDERS(R.string.sliders),
    /** Draw, text, stickers, crop */
    EDIT(R.string.edit),
    /** Saved looks: sliders, filters and frame */
    TEMPLATES(R.string.templates),
}

/** Test tags of the editor's frame (the panels have their own, see PanelTags) */
object EditorTags {
    fun tab(tab: EditorTab) = "editor_tab_${tab.name}"
    fun tabLabel(tab: EditorTab) = "editor_tab_label_${tab.name}"
    const val SAVE = "editor_save"
    const val SAVE_AS = "editor_save_as"
    const val COMPARE = "editor_compare"
    const val PROGRESS = "editor_progress"
    const val FILTER_CENTER = "editor_filter_center"
    const val CROP_APPLY = "editor_crop_apply"
    const val TEXT_FIELD = "editor_text_field"
    const val FRAME = "editor_frame"
    const val PREVIEW_AREA = "editor_preview_area"
    const val CANVAS = "editor_canvas"
    const val CROP_CANCEL = "editor_crop_cancel"
}
