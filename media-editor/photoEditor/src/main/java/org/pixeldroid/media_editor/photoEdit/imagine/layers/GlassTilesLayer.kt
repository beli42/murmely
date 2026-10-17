package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/** Seen through glass blocks: square tiles, each one bending the image like a lens */
class GlassTilesLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterGlassTiles

    override val strengthMode = StrengthMode.SHADER

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            // About 16 square tiles across the longer side, at any image size
            highp float tile = max(size.x, size.y) / 16.0;
            highp vec2 cells = size / tile;
            highp vec2 local = fract(vTexCoords * cells) - 0.5;
            // Each tile shows a slightly magnified, shifted piece of what lies behind it
            highp vec2 uv = vTexCoords - local / cells * 0.6 * uStrength;
            return texture(uImage, clamp(uv, 0.0, 1.0));
        }
    """.trimIndent()
}
