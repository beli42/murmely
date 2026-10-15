package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Tiny planet: the image is rolled up around the centre, the bottom of the image in the middle
 * and the top outside (works best with landscapes and panoramas).
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class TinyPlanetLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterTinyPlanet

    override val strengthMode = StrengthMode.NONE

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 aspect = vec2(size.x / size.y, 1.0);
            highp vec2 p = (vTexCoords - 0.5) * aspect;
            // 1 at the corners
            highp float r = length(p) / length(aspect * 0.5);
            highp float angle = atan(p.y, p.x);
            highp vec2 source = vec2(angle / 6.28318531 + 0.5, 1.0 - r);
            return texture(uImage, clamp(source, 0.0, 1.0));
        }
    """.trimIndent()
}
