package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Lifts (positive intensity) or deepens (negative intensity) only the dark parts
 * of the image, leaving highlights and mid-tones mostly untouched.
 */
class ShadowsLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_shadows

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            float luminance = dot(color.rgb, vec3(0.2125, 0.7154, 0.0721));
            float weight = 1.0 - smoothstep(0.0, 0.55, luminance);
            return vec4(color.rgb + vec3(0.4 * weight), color.a);
        }
    """.trimIndent()
}
