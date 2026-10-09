package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Exposure in photographic stops (intensity 1 = +2 EV, -1 = -2 EV), applied in
 * approximately linear light so it behaves like a real exposure change.
 *
 * The base shader mixes original and processed colour by uIntensity. To get an exact
 * non-linear result we return original + (target - original) / uIntensity, which the
 * base shader's mix turns back into exactly target.
 */
class ExposureLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_exposure

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            if (abs(uIntensity) < 0.0001) return color;
            vec3 linear = pow(max(color.rgb, vec3(0.0)), vec3(2.2));
            linear *= pow(2.0, uIntensity * 2.0);
            vec3 target = pow(clamp(linear, 0.0, 1.0), vec3(1.0 / 2.2));
            return vec4(color.rgb + (target - color.rgb) / uIntensity, color.a);
        }
    """.trimIndent()
}
