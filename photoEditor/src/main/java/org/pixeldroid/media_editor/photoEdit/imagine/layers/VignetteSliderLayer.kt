package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * Adjustable vignette. Positive intensity darkens the edges, negative intensity lightens them.
 * (Separate from [VignetteLayer], which is a fixed preset in the filter list.)
 */
class VignetteSliderLayer: ImagineLayer(initialIntensity = 0f) {

    override val name: Int = R.string.lbl_vignette

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            float dist = distance(vTexCoords, vec2(0.5));
            float falloff = smoothstep(0.25, 0.75, dist);
            return vec4(color.rgb * (1.0 - falloff), color.a);
        }
    """.trimIndent()
}
