package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Waves: the image ripples like water.
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class WavesLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterWaves

    override val strengthMode = StrengthMode.SHADER

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 uv = vTexCoords;
            // Relative to the image, so the preview and the full size look the same
            uv.x += 0.012 * uStrength * sin(uv.y * 40.0);
            uv.y += 0.012 * uStrength * sin(uv.x * 40.0);
            return texture(uImage, clamp(uv, 0.0, 1.0));
        }
    """.trimIndent()
}
