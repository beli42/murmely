package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Fisheye: the whole image is barrel-distorted like through a wide-angle lens.
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class FisheyeLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterFisheye

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 aspect = vec2(size.x / size.y, 1.0);
            highp vec2 p = (vTexCoords - 0.5) * aspect;
            // Distance to a corner, so the corners stay where they are
            highp float cornerSquared = dot(aspect * 0.5, aspect * 0.5);
            highp float k = 0.8;
            p *= (1.0 + k * dot(p, p)) / (1.0 + k * cornerSquared);
            return texture(uImage, clamp(p / aspect + 0.5, 0.0, 1.0));
        }
    """.trimIndent()
}
