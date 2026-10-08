package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Brightens (positive intensity) or recovers/darkens (negative intensity) only the
 * bright parts of the image, leaving shadows and mid-tones mostly untouched.
 */
class HighlightsLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_highlights

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            float luminance = dot(color.rgb, vec3(0.2125, 0.7154, 0.0721));
            float weight = smoothstep(0.45, 1.0, luminance);
            return vec4(color.rgb + vec3(0.4 * weight), color.a);
        }
    """.trimIndent()
}
