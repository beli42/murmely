package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.CenterUse
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Tilt-shift: a sharp band across the photo, more and more blurred above and below it, like a
 * miniature. The band is moved on the photo like a mirror line; the strength sets the blur.
 */
class TiltShiftLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterTiltShift

    override val strengthMode = StrengthMode.SHADER
    override val centerUse = CenterUse.HORIZONTAL_AXIS

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
            // Sharp within the band, blurred from its edge to well away from it
            highp float distance = abs(vTexCoords.y - uCenter.y);
            highp float amount = smoothstep(0.08, 0.35, distance);
            return blurred(uImage, vTexCoords, 0.03 * uStrength * amount, aspect);
        }
    """.trimIndent()
}
