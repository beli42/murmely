package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Film grain. The grain size is defined relative to the image, so the preview and
 * the full-resolution export show the same grain structure.
 */
class GrainLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_grain

    override val source: String = """
        highp float grainHash(highp vec2 p) {
            p = fract(p * vec2(443.897, 441.423));
            p += dot(p, p.yx + 19.19);
            return fract((p.x + p.y) * p.x);
        }

        // Parameter renamed so the global highp vTexCoords is used (stable grain pattern)
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 cells = size / min(size.x, size.y) * 700.0;
            float noise = grainHash(floor(vTexCoords * cells)) - 0.5;
            float luminance = dot(color.rgb, vec3(0.2125, 0.7154, 0.0721));
            float strength = 0.25 * (1.0 - abs(luminance - 0.5));
            return vec4(color.rgb + vec3(noise * strength), color.a);
        }
    """.trimIndent()
}
