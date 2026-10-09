package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Tint. Positive intensity shifts towards magenta, negative towards green.
 */
class TintLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_tint

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            vec3 tinted = color.rgb + vec3(0.06, -0.10, 0.06);
            return vec4(tinted, color.a);
        }
    """.trimIndent()
}
