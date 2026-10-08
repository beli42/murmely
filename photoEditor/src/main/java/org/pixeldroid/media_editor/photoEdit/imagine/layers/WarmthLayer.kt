package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Colour temperature. Positive intensity warms (more orange), negative cools (more blue).
 */
class WarmthLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_warmth

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            vec3 warm = color.rgb + vec3(0.12, 0.03, -0.12);
            return vec4(warm, color.a);
        }
    """.trimIndent()
}
