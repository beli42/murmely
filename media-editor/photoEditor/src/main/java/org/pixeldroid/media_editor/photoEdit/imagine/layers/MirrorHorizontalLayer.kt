package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.CenterUse
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Mirror left-right: the left half, mirrored onto the right half.
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class MirrorHorizontalLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterMirrorHorizontal

    override val strengthMode = StrengthMode.NONE
    override val centerUse = CenterUse.VERTICAL_AXIS

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 uv = vTexCoords;
            // Mirror axis at the tapped x (the middle by default); uStrength >= 0 keeps the
            // left part and mirrors it onto the right, < 0 the other way round
            if (uStrength >= 0.0 ? uv.x > uCenter.x : uv.x < uCenter.x) uv.x = 2.0 * uCenter.x - uv.x;
            return texture(uImage, clamp(uv, 0.0, 1.0));
        }
    """.trimIndent()
}
