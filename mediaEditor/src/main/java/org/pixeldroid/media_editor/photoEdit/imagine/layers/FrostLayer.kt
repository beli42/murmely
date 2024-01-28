package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class FrostLayer: ImagineLayer(initialIntensity = 1f) {

    override val name : String = "Frost"
    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            // Increase brightness
            vec3 black = vec3(0.0);
            vec3 brtColor = mix(black, color.rgb, 1.1);
            
            // Increase blue chanel
            vec3 blueColored = vec3(brtColor.r, brtColor.g, brtColor.b+0.2);
            
            // Vignette
            float blend = 0.1;   
            float dist = distance(vTexCoords, vec2(0.5)) + blend;
            float falloff = 0.5;
            float amount = 0.2;
            vec3 col = blueColored.rgb;
            col *= smoothstep(0.8, falloff * 0.8, dist * (amount + falloff));
            return vec4(mix(col, blueColored.rgb, blend), color.a);  
        }
    """.trimIndent()
}