package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class ContrastLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_contrast

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            vec3 contrasted = (color.rgb - vec3(0.5)) * 2.0 + vec3(0.5);
            return vec4(contrasted, color.a);
        }
    """.trimIndent()
}