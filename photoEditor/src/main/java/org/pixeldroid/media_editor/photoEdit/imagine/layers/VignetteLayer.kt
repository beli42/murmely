package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class VignetteLayer: ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterVignette

    // Inspired by https://www.shadertoy.com/view/MsBBDV
    override val source: String = """
       vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            float blend = 0.2;   
            float dist = distance(vTexCoords, vec2(0.5)) + blend;
            float falloff = 0.5;
            float amount = 0.2;
            
            vec3 col = color.rgb;
            col *= smoothstep(0.8, falloff * 0.8, dist * (amount + falloff));
            
            return vec4(mix(col, color.rgb, blend), color.a);         
        }
    """.trimIndent()
}