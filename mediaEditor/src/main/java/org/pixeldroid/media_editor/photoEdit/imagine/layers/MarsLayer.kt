package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class MarsLayer: ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterMars

    // Inspired by https://github.com/yulu/Instagram_Filter/blob/master/res/raw/hudson_filter_shader.glsl
    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
                        
            vec3 black = vec3(0.0);
            vec3 middle = vec3(0.5);
            vec3 W = vec3(0.2125, 0.7154, 0.0721);
            float luminance = dot(color.rgb, W);
            vec3 gray = vec3(luminance);
            
            // Regulates brightness, contrast and saturation
            vec3 brtColor = mix(black, color.rgb, 1.1);
            vec3 conColor = mix(middle, brtColor, 1.3);
            vec3 satColor = mix(gray, conColor, 1.3);
             
            return vec4(satColor, color.a);     
        }
    """.trimIndent()

}