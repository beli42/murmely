package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Mirror left-right: the left half, mirrored onto the right half.
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class MirrorHorizontalLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterMirrorHorizontal

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 uv = vTexCoords;
            if (uv.x > 0.5) uv.x = 1.0 - uv.x;
            return texture(uImage, uv);
        }
    """.trimIndent()
}
