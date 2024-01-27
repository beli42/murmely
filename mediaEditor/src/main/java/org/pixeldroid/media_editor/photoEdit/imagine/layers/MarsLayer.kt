package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class MarsLayer: ImagineLayer(initialIntensity = 1f) {

    override val name : String = "Mars"
    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            float saturation = 1.5;
            vec3 luminance = vec3(0.3086, 0.6094, 0.0820);
            float oneMinusSat = 1.0 - saturation;

            vec3 red = vec3(luminance.x * oneMinusSat);
            vec3 green = vec3(luminance.y * oneMinusSat);
            vec3 blue = vec3(luminance.z * oneMinusSat);
            red+= vec3(saturation, 0, 0);
            green += vec3(0, saturation, 0);
            blue += vec3(0, 0, saturation);

            vec4 sat = mat4(red,     0,
                        green,   0,
                        blue,    0,
                        0, 0, 0, 1) * color;
        
            vec3 res = (sat.rgb - vec3(0.5)) * 1.5 + vec3(0.5);
            return vec4(res, color.a);            
        }
    """.trimIndent()

}