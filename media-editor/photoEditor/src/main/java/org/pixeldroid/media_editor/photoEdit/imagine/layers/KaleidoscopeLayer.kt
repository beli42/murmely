package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.CenterUse
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Kaleidoscope: six mirrored segments around the centre.
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class KaleidoscopeLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterKaleidoscope

    override val strengthMode = StrengthMode.NONE
    override val centerUse = CenterUse.POINT

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 aspect = vec2(size.x / size.y, 1.0);
            highp vec2 p = (vTexCoords - uCenter) * aspect;
            highp float segment = 6.28318531 / 6.0;
            highp float angle = mod(atan(p.y, p.x), segment);
            // Mirror every other half segment
            if (angle > segment * 0.5) angle = segment - angle;
            p = length(p) * vec2(cos(angle), sin(angle));
            return texture(uImage, clamp(p / aspect + uCenter, 0.0, 1.0));
        }
    """.trimIndent()
}
