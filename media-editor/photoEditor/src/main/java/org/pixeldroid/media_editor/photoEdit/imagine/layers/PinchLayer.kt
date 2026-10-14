package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Pinch: the centre is pulled together, the opposite of the sphere.
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class PinchLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterPinch

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 aspect = vec2(size.x / size.y, 1.0);
            highp vec2 p = (vTexCoords - 0.5) * aspect;
            highp float radius = 0.5 * min(aspect.x, 1.0);
            highp float r = length(p);
            if (r < radius && r > 0.0) {
                // Read from further out: shrinks the middle, continuous at the edge
                highp float n = r / radius;
                p *= pow(n, 0.6) / n;
            }
            return texture(uImage, clamp(p / aspect + 0.5, 0.0, 1.0));
        }
    """.trimIndent()
}
