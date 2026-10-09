package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Clarity: local contrast in the mid-tones. Compares each pixel with a wide blur of its
 * surroundings and amplifies the difference, mostly in mid-tones so highlights and
 * shadows don't clip. Negative intensity gives a soft, "dreamy" look.
 * The radius scales with the image size so preview and export match.
 */
class ClarityLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_clarity

    override val source: String = """
        // Parameter renamed so the global highp vTexCoords is used for exact offsets
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 r = vec2(min(size.x, size.y) / 80.0) / size;
            vec3 blur = vec3(0.0);
            for (int i = 0; i < 8; i++) {
                float a = float(i) * 0.785398;
                highp vec2 dir = vec2(cos(a), sin(a));
                blur += texture(uImage, vTexCoords + dir * r).rgb;
                blur += texture(uImage, vTexCoords + dir * r * 0.5).rgb;
            }
            blur /= 16.0;
            float luminance = dot(color.rgb, vec3(0.2125, 0.7154, 0.0721));
            float midtones = 1.0 - abs(luminance - 0.5) * 2.0;
            vec3 clarified = color.rgb + (color.rgb - blur) * 1.5 * midtones;
            return vec4(clamp(clarified, 0.0, 1.0), color.a);
        }
    """.trimIndent()
}
