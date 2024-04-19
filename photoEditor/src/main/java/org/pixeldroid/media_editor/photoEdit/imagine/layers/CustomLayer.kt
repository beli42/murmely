package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class CustomLayer(override val customName: String?, override val source: String) :
    ImagineLayer(initialIntensity = 1f) {
    /**
     * We don't use the [name] in a [CustomLayer], but the [customName]
     */
    override val name = null
}