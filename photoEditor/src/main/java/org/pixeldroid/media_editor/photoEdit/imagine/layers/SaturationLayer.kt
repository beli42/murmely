package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class SaturationLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_saturation

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            vec3 W = vec3(0.2125, 0.7154, 0.0721);
            float luminance = dot(color.rgb, W);
            vec3 gray = vec3(luminance);
            vec3 satColor = mix(gray, color.rgb, 2.0);
            
            return vec4(satColor, color.a);   
        }
    """.trimIndent()
}