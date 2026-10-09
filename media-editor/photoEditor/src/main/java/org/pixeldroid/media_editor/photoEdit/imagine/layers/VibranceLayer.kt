package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Vibrance: like saturation, but mostly boosts muted colours and leaves already
 * saturated colours (and skin tones, which are usually moderately saturated) alone.
 * Negative intensity mutes colours in the same selective way.
 */
class VibranceLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_vibrance

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            float maxC = max(max(color.r, color.g), color.b);
            float minC = min(min(color.r, color.g), color.b);
            float saturation = maxC - minC;
            float luminance = dot(color.rgb, vec3(0.2125, 0.7154, 0.0721));
            float amount = 1.5 * (1.0 - smoothstep(0.0, 0.6, saturation));
            vec3 vibrant = mix(vec3(luminance), color.rgb, 1.0 + amount);
            return vec4(vibrant, color.a);
        }
    """.trimIndent()
}
