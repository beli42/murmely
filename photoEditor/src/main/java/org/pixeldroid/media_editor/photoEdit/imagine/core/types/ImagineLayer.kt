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
     * Filter name. If null fall back to [customName]
     */
    @get:StringRes
    abstract val name: Int?

    /**
     * Filter name: fall back to this if [name] is null.
     */
    open val customName: String? = null

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

    fun shaderId(): Int {
        return source.hashCode()
    }

    fun resetIntensity(){
        intensity = initialIntensity
    }
}