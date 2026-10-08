package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Unsharp mask. Positive intensity sharpens, negative intensity softens the image
 * (the base shader interpolates past the original towards a blur).
 */
class SharpnessLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_sharpness

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            vec2 texel = 1.0 / vec2(textureSize(uImage, 0));
            vec3 blur = (
                texture(uImage, vTexCoords + vec2(-texel.x, 0.0)).rgb +
                texture(uImage, vTexCoords + vec2( texel.x, 0.0)).rgb +
                texture(uImage, vTexCoords + vec2(0.0, -texel.y)).rgb +
                texture(uImage, vTexCoords + vec2(0.0,  texel.y)).rgb
            ) * 0.25;
            vec3 sharpened = color.rgb + (color.rgb - blur) * 2.0;
            return vec4(clamp(sharpened, 0.0, 1.0), color.a);
        }
    """.trimIndent()
}
