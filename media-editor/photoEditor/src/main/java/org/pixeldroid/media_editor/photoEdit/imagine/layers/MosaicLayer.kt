package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Mosaic: large square blocks of one colour.
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class MosaicLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterMosaic

    override val strengthMode = StrengthMode.SHADER

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            // About 60 blocks across the longer side at strength 1, at any image size;
            // stronger = bigger blocks, 0 = single pixels (the image unchanged)
            highp float block = max(1.0, max(size.x, size.y) / 60.0 * uStrength);
            highp vec2 cells = size / block;
            highp vec2 uv = (floor(vTexCoords * cells) + 0.5) / cells;
            return texture(uImage, clamp(uv, 0.0, 1.0));
        }
    """.trimIndent()
}
