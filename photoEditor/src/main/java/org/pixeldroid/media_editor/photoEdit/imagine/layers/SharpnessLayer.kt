package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Unsharp mask. Positive intensity sharpens, negative intensity softens the image
 * (the base shader interpolates past the original towards a blur).
 *
 * The sampling radius scales with the image size, so the full-resolution export looks
 * the same as the (smaller) on-screen preview.
 */
class SharpnessLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_sharpness

    override val source: String = """
        // Parameter renamed so the global highp vTexCoords is used for exact offsets
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp float radius = max(1.0, min(size.x, size.y) / 1200.0);
            highp vec2 d = radius / size;
            vec3 blur = (
                texture(uImage, vTexCoords + vec2(-d.x, 0.0)).rgb +
                texture(uImage, vTexCoords + vec2( d.x, 0.0)).rgb +
                texture(uImage, vTexCoords + vec2(0.0, -d.y)).rgb +
                texture(uImage, vTexCoords + vec2(0.0,  d.y)).rgb
            ) * 0.25;
            vec3 sharpened = color.rgb + (color.rgb - blur) * 2.0;
            return vec4(clamp(sharpened, 0.0, 1.0), color.a);
        }
    """.trimIndent()
}
