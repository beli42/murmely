package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class ElsaLayer: ImagineLayer(initialIntensity = 1f) {

    override val name : String = "Elsa"

    // Inspired by https://github.com/yulu/Instagram_Filter/blob/master/res/raw/hudson_filter_shader.glsl
    override val source: String = """
       vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            
            vec3 black = vec3(0.0);
            vec3 middle = vec3(0.5);
            vec3 W = vec3(0.2125, 0.7154, 0.0721);
            float luminance = dot(color.rgb, W);
            vec3 gray = vec3(luminance);
            
            // Regulate brightness, contrast and saturation
            vec3 brtColor = mix(black, color.rgb, 1.0);
            vec3 conColor = mix(middle, brtColor, 1.2);
            vec3 satColor = mix(gray, conColor, 1.2);
            
            // Vignette
            float bl = 0.2;   
            float dist = distance(vTexCoords, vec2(0.5, 0.5)) + bl;
            float falloff = 0.5;
            float amount = 0.2;
            satColor *= smoothstep(0.8, falloff * 0.8, dist * (amount + falloff));
            return vec4(mix(satColor, color.rgb, bl), color.a);               
        }
    """.trimIndent()

}