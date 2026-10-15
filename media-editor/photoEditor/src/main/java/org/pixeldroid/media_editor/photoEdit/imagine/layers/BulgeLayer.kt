package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.CenterUse
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Sphere: the centre bulges out as if under a glass ball.
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class BulgeLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterBulge

    override val strengthMode = StrengthMode.SHADER
    override val centerUse = CenterUse.POINT

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 aspect = vec2(size.x / size.y, 1.0);
            highp vec2 p = (vTexCoords - uCenter) * aspect;
            highp float radius = 0.5 * min(aspect.x, 1.0);
            highp float r = length(p);
            if (r < radius && r > 0.0) {
                // Read from closer to the centre: magnifies the middle, continuous at the edge
                highp float n = r / radius;
                p *= pow(n, 1.0 + 0.7 * uStrength) / n;
            }
            return texture(uImage, clamp(p / aspect + uCenter, 0.0, 1.0));
        }
    """.trimIndent()
}
