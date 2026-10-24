package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.CenterUse
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Fisheye: the whole image is barrel-distorted like through a wide-angle lens.
 *
 * Geometric filter: reads each pixel from another place of the image. Coordinates are corrected
 * for the aspect ratio, so circles stay round on non-square images.
 */
class FisheyeLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterFisheye

    override val strengthMode = StrengthMode.SHADER
    override val centerUse = CenterUse.POINT

    override val usesSize = true
    override val sizeReach = 0.7f

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec2 aspect = vec2(size.x / size.y, 1.0);
            highp vec2 p = (vTexCoords - uCenter) * aspect;
            // Distance to the farthest corner, so the corners stay where they are
            highp vec2 corner = max(uCenter, 1.0 - uCenter) * aspect;
            highp float cornerSquared = dot(corner, corner);
            // 40 % more than at first (0.8)
            highp float k = 1.12 * uStrength;
            // The lens reaches to the farthest corner at size 1 (the whole image, as before);
            // smaller or larger with the size. Outside of it the image stays as it is
            highp float lensSquared = cornerSquared * uSize * uSize;
            highp float q = dot(p, p) / lensSquared;
            if (q < 1.0) p *= (1.0 + k * cornerSquared * q) / (1.0 + k * cornerSquared);
            return texture(uImage, clamp(p / aspect + uCenter, 0.0, 1.0));
        }
    """.trimIndent()
}
