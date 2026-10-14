package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Mirror top-bottom: the top half, mirrored onto the bottom half (like a lake).
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class MirrorVerticalLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterMirrorVertical

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 uv = vTexCoords;
            if (uv.y > 0.5) uv.y = 1.0 - uv.y;
            return texture(uImage, uv);
        }
    """.trimIndent()
}
