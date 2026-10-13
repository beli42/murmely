package org.pixeldroid.media_editor.videoEdit

/** How [VideoEditArguments] translate into a conversion. Pure functions, no Android dependencies. */
object VideoEditPlan {

    /** Speeds offered in the editor */
    val SPEEDS: List<Float> = listOf(0.5f, 1f, 2f, 4f, 8f)
    const val NORMAL_SPEED_INDEX = 1

    fun speed(arguments: VideoEditArguments): Float = SPEEDS.getOrElse(arguments.speedIndex) { 1f }

    /** The audio is removed when muted, and when the speed changes (it would sound distorted) */
    fun removeAudio(arguments: VideoEditArguments): Boolean =
        arguments.muted || arguments.speedIndex != NORMAL_SPEED_INDEX

    /** Part of the source to keep: start in ms, end in ms or null for "until the end" */
    fun clippingMs(arguments: VideoEditArguments): Pair<Long, Long?> {
        val start = ((arguments.videoStart ?: 0f) * 1000).toLong().coerceAtLeast(0)
        val end = arguments.videoEnd?.let { (it * 1000).toLong() }?.takeIf { it > start }
        return start to end
    }

    /** Rectangle in normalized device coordinates: x from -1 (left) to 1, y from -1 (bottom) to 1 */
    data class Ndc(val left: Float, val right: Float, val bottom: Float, val top: Float)

    /** The crop as used by Media3's Crop effect */
    fun cropNdc(crop: RelativeCropPosition): Ndc {
        fun x(relative: Float) = (-1f + 2f * relative).coerceIn(-1f, 1f)
        fun y(relativeFromTop: Float) = (1f - 2f * relativeFromTop).coerceIn(-1f, 1f)
        return Ndc(
            left = x(crop.relativeX),
            right = x(crop.relativeX + crop.relativeWidth),
            bottom = y(crop.relativeY + crop.relativeHeight),
            top = y(crop.relativeY),
        )
    }

    /** Length of the converted video in seconds, for a source of [sourceSeconds] */
    fun outputSeconds(sourceSeconds: Float, arguments: VideoEditArguments): Float {
        val (startMs, endMs) = clippingMs(arguments)
        val keptMs = (endMs ?: (sourceSeconds * 1000).toLong()).coerceAtMost((sourceSeconds * 1000).toLong()) - startMs
        return keptMs.coerceAtLeast(0) / 1000f / speed(arguments)
    }
}
