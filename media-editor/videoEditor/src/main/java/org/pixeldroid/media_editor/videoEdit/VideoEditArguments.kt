package org.pixeldroid.media_editor.videoEdit

import java.io.Serializable
import kotlin.math.absoluteValue

/** Part of the video to keep, relative to its size (0..1, origin top left) */
data class RelativeCropPosition(
    /** Width of the selected part, relative to the width of the video */
    val relativeWidth: Float = 1f,
    /** Height of the selected part, relative to the height of the video */
    val relativeHeight: Float = 1f,
    /** Distance of the left edge of the selected part, relative to the width of the video */
    val relativeX: Float = 0f,
    /** Distance of the top edge of the selected part, relative to the height of the video */
    val relativeY: Float = 0f,
) : Serializable {
    fun notCropped(): Boolean =
        (relativeWidth - 1f).absoluteValue < 0.001f
            && (relativeHeight - 1f).absoluteValue < 0.001f
            && relativeX.absoluteValue < 0.001f
            && relativeY.absoluteValue < 0.001f
}

/**
 * What to do with a video, chosen in the video editor and passed back to the app.
 *
 * @param muted remove the audio
 * @param videoStart start of the part to keep, in seconds, or null to keep from the beginning
 * @param videoEnd end of the part to keep, in seconds, or null to keep until the end
 * @param speedIndex index in [VideoEditPlan.SPEEDS]
 */
data class VideoEditArguments(
    val muted: Boolean,
    val videoStart: Float?,
    val videoEnd: Float?,
    val speedIndex: Int,
    val videoCrop: RelativeCropPosition,
) : Serializable
