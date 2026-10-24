package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.CenterUse
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Focus: sharp around a point (tapped on the photo), more and more blurred away from it, like a
 * portrait with a blurred background. The strength sets the blur.
 */
class FocusLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterFocus

    override val strengthMode = StrengthMode.SHADER
    override val centerUse = CenterUse.POINT

    override val usesSize = true
    override val sizeReach = 0.12f

    override val source: String = """
        // Blur: samples on a spiral around the point, turned a little differently for every
        // pixel, so the pattern shows as fine noise instead of rings. radius: of the height
        vec4 blurred(sampler2D image, highp vec2 uv, highp float radius, highp vec2 aspect) {
            if (radius < 0.0005) return texture(image, uv);
            highp float turn = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453) * 6.28318531;
            vec4 sum = vec4(0.0);
            for (int i = 0; i < 32; i++) {
                highp float f = (float(i) + 0.5) / 32.0;
                highp float a = float(i) * 2.39996323 + turn;
                highp vec2 offset = vec2(cos(a), sin(a)) * sqrt(f) * radius / aspect;
                sum += texture(image, clamp(uv + offset, 0.0, 1.0));
            }
            return sum / 32.0;
        }

        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 aspect = vec2(size.x / size.y, 1.0);
            // In parts of the shorter side: round on any photo
            highp float distance = length((vTexCoords - uCenter) * aspect) / min(aspect.x, 1.0);
            // The sharp area (and the change to blurred) grows with the size
            highp float amount = smoothstep(0.12 * uSize, 0.5 * uSize, distance);
            return blurred(uImage, vTexCoords, 0.03 * uStrength * amount, aspect);
        }
    """.trimIndent()
}
