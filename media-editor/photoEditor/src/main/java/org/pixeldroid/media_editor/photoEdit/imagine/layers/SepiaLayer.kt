package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class SepiaLayer: ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterSepia

    override val source: String = """
       vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            vec3 bw = vec3(color.r * 0.3 + color.g * 0.59 + color.b * 0.11);
            vec3 yellowed = vec3(bw.r+0.25, bw.g+0.15, bw.b);
             
            return vec4(yellowed, color.a);            
        }
    """.trimIndent()

}