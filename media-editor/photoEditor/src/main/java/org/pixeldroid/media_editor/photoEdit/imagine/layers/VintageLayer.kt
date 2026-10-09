package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class VintageLayer: ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterVintage

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            // Increase brightness
            vec3 black = vec3(0.0);
            vec3 brtColor = mix(black, color.rgb, 1.06);
            
            // Increase red and green chanel
            return vec4(brtColor.r+0.2, brtColor.g+0.1, brtColor.b, color.a);
        }
    """.trimIndent()
}