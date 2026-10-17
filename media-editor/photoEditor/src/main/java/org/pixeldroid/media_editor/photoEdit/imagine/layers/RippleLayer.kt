package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.CenterUse
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/** Rings of waves around a centre, like a drop falling into water; fading towards the edge */
class RippleLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterRipple

    override val strengthMode = StrengthMode.SHADER
    override val centerUse = CenterUse.POINT

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 aspect = vec2(size.x / size.y, 1.0);
            highp vec2 p = (vTexCoords - uCenter) * aspect;
            highp float r = length(p);
            if (r > 0.0001) {
                // Pushed in and out along the radius; weaker further out
                highp float wave = sin(r * 60.0) * 0.012 * uStrength * exp(-r * 2.5);
                p += p / r * wave;
            }
            return texture(uImage, clamp(p / aspect + uCenter, 0.0, 1.0));
        }
    """.trimIndent()
}
