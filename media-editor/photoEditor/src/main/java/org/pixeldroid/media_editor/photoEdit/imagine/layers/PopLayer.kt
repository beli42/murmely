package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/** Vivid colours: much more saturation, a little more contrast, muted colours lifted most */
class PopLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterPop

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            vec3 W = vec3(0.2125, 0.7154, 0.0721);
            float luminance = dot(color.rgb, W);
            // Vibrance: the less saturated a colour is, the more it is boosted
            float maxC = max(color.r, max(color.g, color.b));
            float minC = min(color.r, min(color.g, color.b));
            float saturation = maxC - minC;
            float boost = 1.35 + 0.5 * (1.0 - saturation);
            vec3 vivid = mix(vec3(luminance), color.rgb, boost);
            // A little more contrast around the middle
            vec3 punchy = mix(vec3(0.5), vivid, 1.12);
            return vec4(clamp(punchy, 0.0, 1.0), color.a);
        }
    """.trimIndent()
}
