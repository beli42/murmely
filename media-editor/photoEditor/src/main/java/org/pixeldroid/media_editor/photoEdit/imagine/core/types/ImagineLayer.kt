package org.pixeldroid.media_editor.photoEdit.imagine.core.types

import android.opengl.GLES20

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
     * Called during rendering to bind any uniform, see [setUniform]
     */
    open fun bind(program: ImagineShader.Program) {}

    /** Uniform locations by program and name */
    private val uniformLocations = HashMap<Pair<Int, String>, Int>()

    /**
     * Sets a float or float array uniform of this layer's shader, e.g. a strength or a centre.
     * Makes [program] the current program first: [bind] is called before the engine does.
     */
    protected fun setUniform(program: ImagineShader.Program, name: String, vararg values: Float) {
        program.use()
        val location = uniformLocations.getOrPut(program.program to name) {
            GLES20.glGetUniformLocation(program.program, name)
        }
        if (location < 0) return   // not used by the shader (removed by the compiler)
        GLES20.glUniform1fv(location, values.size, values, 0)
    }

    fun shaderId(): Int {
        return source.hashCode()
    }

    fun resetIntensity(){
        intensity = initialIntensity
    }
}