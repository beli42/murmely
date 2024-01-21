package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class BrightnessLayer: ImagineLayer(initialIntensity = 0f) {

    override val source: String = """
        vec4 process(vec4 color) {
            vec3 white = vec3(255);
            return vec4(white, color.a);
        }
    """.trimIndent()
}