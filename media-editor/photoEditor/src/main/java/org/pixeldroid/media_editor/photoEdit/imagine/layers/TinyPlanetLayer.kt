package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Tiny planet: the image is rolled up into a small round world, its bottom in the middle and
 * its top around the outside. Made for ordinary photos, not only panoramas: the middle of the
 * photo stands upright on top of the planet, the planet fits the shorter side, and the photo's
 * left and right edges meet at the bottom, blended softly so there is no seam.
 * The strength bends the photo from unchanged (0) to a full planet (100 %), beyond that the
 * ground gets more room.
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class TinyPlanetLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterTinyPlanet

    override val strengthMode = StrengthMode.SHADER

    override val source: String = """
        highp vec2 planetAt(highp float u, highp float v, highp float k) {
            return clamp(mix(vTexCoords, vec2(u, v), k), 0.0, 1.0);
        }

        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 aspect = vec2(size.x / size.y, 1.0);
            highp vec2 p = (vTexCoords - 0.5) * aspect;
            // 1 at half the shorter side: the photo's top just reaches the nearest edge
            highp float r = clamp(length(p) / (0.5 * min(aspect.x, 1.0)), 0.0, 1.0);
            // Round the circle: the photo's middle (0.5) on top, its edges meet at the bottom
            highp float around = fract(atan(p.x, -p.y) / 6.28318531 + 0.5);
            highp float curve = 1.1 + 0.4 * max(uStrength - 1.0, 0.0);
            highp float v = 1.0 - pow(r, curve);
            highp float k = clamp(uStrength, 0.0, 1.0);
            vec4 plain = texture(uImage, planetAt(around, v, k));
            // Near the bottom, mirrored: both edges show the same, so they meet without a seam
            vec4 mirrored = texture(uImage, planetAt(1.0 - abs(2.0 * around - 1.0), v, k));
            highp float w = 1.0 - (1.0 - smoothstep(0.0, 0.06, min(around, 1.0 - around))) * k;
            return mix(mirrored, plain, w);
        }
    """.trimIndent()
}
