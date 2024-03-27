package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class NegativeLayer: ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterNegative

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            return vec4(vec3(1,1,1) - color.rgb, color.a);
        }
    """.trimIndent()
}