package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.CenterUse
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode

/**
 * Zoom: the whole image evenly larger or smaller, around the tapped point. The strength is the
 * size: 100 % unchanged, 200 % twice as large, 50 % half as large (black around it), at least 10 %.
 *
 * Geometric filter: reads each pixel from another place of the image.
 */
class ZoomLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterZoom

    override val strengthMode = StrengthMode.SHADER
    override val centerUse = CenterUse.POINT

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            highp float zoom = max(uStrength, 0.1);
            highp vec2 uv = (vTexCoords - uCenter) / zoom + uCenter;
            // Smaller than the image: black around it
            if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec4(0.0, 0.0, 0.0, 1.0);
            return texture(uImage, uv);
        }
    """.trimIndent()
}
