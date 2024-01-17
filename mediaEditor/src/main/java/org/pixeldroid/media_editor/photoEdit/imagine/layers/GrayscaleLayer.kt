package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class GrayscaleLayer: ImagineLayer(initialIntensity = 0f) {

    override val source: String = """
        vec4 process(vec4 color) {
            vec3 avg = vec3(dot(vec3(0.2126, 0.7152, 0.0722), color.rgb));
            return vec4(avg, color.a);
        }
    """.trimIndent()
}