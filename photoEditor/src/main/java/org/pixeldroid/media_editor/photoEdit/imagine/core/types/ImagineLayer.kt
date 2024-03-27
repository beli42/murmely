package org.pixeldroid.media_editor.photoEdit.imagine.core.types

import androidx.annotation.StringRes
import org.pixeldroid.media_editor.photoEdit.imagine.core.objects.ImagineShader

/**
 * Abstract for a image processing layer
 */
abstract class ImagineLayer(val initialIntensity: Float) {

    /**
     * Source code of the fragment shader code snippet
     */
    abstract val source: String

    /**
     * Filter name
     */
    @get:StringRes
    abstract val name: Int

    /**
     * Intensity of application of this layer
     */
    var intensity: Float = initialIntensity

    /**
     * Type of blending to apply between this Layer and the previous Layer
     */
    open val blendMode: ImagineBlendMode = ImagineBlendMode.Normal

    /**
     * Called during shader creation to bind any uniform
     */
    open fun create(program: ImagineShader.Program) {}

    /**
     * Called during rendering to bind any uniform
     */
    open fun bind(program: ImagineShader.Program) {}

    fun resetIntensity(){
        intensity = initialIntensity
    }
}