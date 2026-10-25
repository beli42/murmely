package org.pixeldroid.media_editor.common

const val PICTURE_URI = "picture_uri"
const val PICTURE_POSITION = "picture_position"

/**
 * Optional Int extra for the photo and video editors and the collage: percent of the width for
 * the photo or video in landscape, the tools beside it. Set in the app's settings.
 */
const val LANDSCAPE_PHOTO_PERCENT = "landscape_photo_percent"
const val LANDSCAPE_PHOTO_DEFAULT = 70
const val LANDSCAPE_PHOTO_MIN = 50
const val LANDSCAPE_PHOTO_MAX = 85

/**
 * The sizes offered in the settings, from smaller to bigger, as percent: the most the photo may
 * take (a tall photo takes less, see EditorLayout.landscapePhotoWidth)
 */
val LANDSCAPE_PHOTO_STEPS = listOf(50, 60, 70, 78, 85)

/** [percent] as a share (0..1), limited to the allowed range */
fun landscapePhotoShare(percent: Int): Float = percent.coerceIn(LANDSCAPE_PHOTO_MIN, LANDSCAPE_PHOTO_MAX) / 100f

/**
 * Optional Int extra for the photo editor: how long the photo is held still before it is shown
 * over everything until the finger lifts ("click zoom"), in ms; 0: never. Set in the app's settings.
 */
const val CLICK_ZOOM_MS = "click_zoom_ms"
const val CLICK_ZOOM_DEFAULT = 3000

/** The times offered in the settings (ms), 0 first: off */
val CLICK_ZOOM_STEPS = listOf(0, 1000, 2000, 3000, 5000)

/**
 * Optional Int extras for the photo editor: in how many rows (portrait) and columns (landscape)
 * the filter thumbnails are shown, [FILTER_LINES_MIN] to [FILTER_LINES_MAX] each; they share
 * the room. Set in the app's settings.
 */
const val FILTER_ROWS = "filter_rows"
const val FILTER_COLUMNS = "filter_columns"
const val FILTER_ROWS_DEFAULT = 1
const val FILTER_COLUMNS_DEFAULT = 2
const val FILTER_LINES_MIN = 1
const val FILTER_LINES_MAX = 3

/**
 * Optional Int extras for the photo editor: in how many columns the adjustment sliders are shown,
 * in portrait and in landscape, 1 or [SLIDER_COLUMNS_MAX]. Set in the app's settings.
 */
const val SLIDER_COLUMNS_PORTRAIT = "slider_columns_portrait"
const val SLIDER_COLUMNS_LANDSCAPE = "slider_columns_landscape"
const val SLIDER_COLUMNS_DEFAULT = 1
const val SLIDER_COLUMNS_MAX = 2
