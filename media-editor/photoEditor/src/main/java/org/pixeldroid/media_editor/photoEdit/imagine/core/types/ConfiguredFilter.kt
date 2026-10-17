package org.pixeldroid.media_editor.photoEdit.imagine.core.types

import androidx.annotation.StringRes
import org.pixeldroid.media_editor.photoEdit.imagine.core.objects.ImagineShader

/** What the strength of a filter does */
enum class StrengthMode(val max: Float) {
    /** Blends the filtered image with the unfiltered one (colour filters): 0..100 % */
    MIX(1f),

    /** Scales the effect in the shader (uStrength, e.g. how far a swirl twists): 0..200 % */
    SHADER(2f),

    /** No strength (e.g. a mirror can't be half mirrored) */
    NONE(1f),
}

/**
 * Which part a mirror keeps: decided by the first move of its line. Moved right (or down),
 * the left (top) part is mirrored onto the right (bottom); moved left (up), the other way
 * round. Until it is moved, like START.
 */
enum class MirrorPart { UNDECIDED, START, END }

/** What tapping the photo sets for a filter (uCenter in the shader) */
enum class CenterUse {
    NONE,

    /** The centre of the effect */
    POINT,

    /** A vertical line at the tapped x (mirror left/right) */
    VERTICAL_AXIS,

    /** A horizontal line at the tapped y (mirror top/bottom) */
    HORIZONTAL_AXIS,
}

/**
 * Settings of a filter, immutable so the edit history can keep them.
 * [strength] 1 = the filter as designed; the centre is relative to the image (0..1, y down).
 */
data class FilterSettings(
    val strength: Float = 1f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    /** Mirrors: which part is kept and mirrored onto the other, see [MirrorPart] */
    val mirrorPart: MirrorPart = MirrorPart.UNDECIDED,
) {
    companion object {
        val DEFAULT = FilterSettings()
    }
}

/**
 * [filter] with [settings]. A new object for every change of the settings: the filter objects
 * themselves stay unchanged (the thumbnails use them), and the same filter can be applied twice
 * with different settings. Uses the same compiled shader as [filter].
 */
data class ConfiguredFilter(val filter: ImagineLayer, val settings: FilterSettings) :
    ImagineLayer(filter.initialIntensity) {

    init {
        intensity = if (filter.strengthMode == StrengthMode.MIX) settings.strength.coerceIn(0f, 1f) else 1f
    }

    override val source: String get() = filter.source
    @get:StringRes
    override val name: Int? get() = filter.name
    override val customName: String? get() = filter.customName
    override val blendMode: ImagineBlendMode get() = filter.blendMode
    override val strengthMode: StrengthMode get() = filter.strengthMode
    override val centerUse: CenterUse get() = filter.centerUse

    override val shaderStrength: Float
        get() = when {
            filter.strengthMode == StrengthMode.SHADER -> settings.strength
            // Mirrors: the sign says which part is kept (negative: the right / bottom one)
            settings.mirrorPart == MirrorPart.END && isMirror -> -1f
            else -> 1f
        }

    private val isMirror: Boolean
        get() = filter.centerUse == CenterUse.VERTICAL_AXIS || filter.centerUse == CenterUse.HORIZONTAL_AXIS
    override val centerX: Float get() = if (filter.centerUse == CenterUse.NONE) 0.5f else settings.centerX
    override val centerY: Float get() = if (filter.centerUse == CenterUse.NONE) 0.5f else settings.centerY

    override fun create(program: ImagineShader.Program) = filter.create(program)
    override fun bind(program: ImagineShader.Program) = filter.bind(program)
}

/** The filter itself, without settings */
val ImagineLayer.base: ImagineLayer get() = (this as? ConfiguredFilter)?.filter ?: this

/** The settings of this filter (the default ones if it has none) */
val ImagineLayer.settings: FilterSettings get() = (this as? ConfiguredFilter)?.settings ?: FilterSettings.DEFAULT

/** The filter can be changed by the user: it has a strength or a centre */
val ImagineLayer.isAdjustable: Boolean get() = strengthMode != StrengthMode.NONE || centerUse != CenterUse.NONE
