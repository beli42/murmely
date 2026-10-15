package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.CenterUse
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Mirror top-bottom: the top half, mirrored onto the bottom half (like a lake).
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class MirrorVerticalLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterMirrorVertical

    override val strengthMode = StrengthMode.NONE
    override val centerUse = CenterUse.HORIZONTAL_AXIS

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 uv = vTexCoords;
            // Mirror axis at the tapped y (the middle by default)
            if (uv.y > uCenter.y) uv.y = 2.0 * uCenter.y - uv.y;
            return texture(uImage, clamp(uv, 0.0, 1.0));
        }
    """.trimIndent()
}
