package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class BlackWhiteLayer: ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterBlackAndWhite

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            return vec4(vec3(color.r * 0.3 + color.g * 0.59 + color.b * 0.11), color.a);
        }
    """.trimIndent()
}