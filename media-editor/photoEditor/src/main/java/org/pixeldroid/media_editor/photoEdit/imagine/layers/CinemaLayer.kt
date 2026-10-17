package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/** Film look: teal shadows, warm orange highlights, slightly more contrast */
class CinemaLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int = R.string.filterCinema

    override val source: String = """
        vec4 process(vec4 color, sampler2D uImage, vec2 texCoordsMediump) {
            vec3 W = vec3(0.2125, 0.7154, 0.0721);
            float luminance = dot(color.rgb, W);
            vec3 teal = vec3(0.0, 0.45, 0.5);
            vec3 orange = vec3(1.0, 0.6, 0.3);
            // Shadows towards teal, highlights towards orange, midtones mostly kept
            float shadows = 1.0 - smoothstep(0.0, 0.5, luminance);
            float highlights = smoothstep(0.5, 1.0, luminance);
            vec3 toned = color.rgb;
            toned = mix(toned, toned * 0.6 + teal * 0.4 * luminance * 2.0, shadows * 0.5);
            toned = mix(toned, toned * 0.7 + orange * 0.3, highlights * 0.45);
            vec3 contrasted = mix(vec3(0.5), toned, 1.1);
            return vec4(clamp(contrasted, 0.0, 1.0), color.a);
        }
    """.trimIndent()
}
