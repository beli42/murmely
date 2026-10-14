package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Swirl: the image twists towards the centre, more strongly closer to it.
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class SwirlLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterSwirl

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 aspect = vec2(size.x / size.y, 1.0);
            highp vec2 p = (vTexCoords - 0.5) * aspect;
            highp float radius = 0.5 * min(aspect.x, 1.0);
            highp float r = length(p);
            if (r < radius) {
                highp float t = 1.0 - r / radius;
                highp float angle = t * t * 3.0;
                highp float s = sin(angle);
                highp float c = cos(angle);
                p = vec2(c * p.x - s * p.y, s * p.x + c * p.y);
            }
            return texture(uImage, clamp(p / aspect + 0.5, 0.0, 1.0));
        }
    """.trimIndent()
}
