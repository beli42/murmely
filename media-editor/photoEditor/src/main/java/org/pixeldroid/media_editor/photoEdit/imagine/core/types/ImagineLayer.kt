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

    /** What the strength of [FilterSettings] does for this filter */
    open val strengthMode: StrengthMode = StrengthMode.MIX

    /** What tapping the photo sets for this filter */
    open val centerUse: CenterUse = CenterUse.NONE

    /** Passed to the shader as uStrength (1 = as designed), see [ConfiguredFilter] */
    open val shaderStrength: Float get() = 1f

    /**
     * Whether the size of the area the filter works on can be changed (two fingers on the photo);
     * the shader then uses uSize for its radius or band width
     */
    open val usesSize: Boolean = false

    /**
     * How far the effect reaches at size 1, in parts of the image's shorter side (a point), or of
     * its height (half the band around a line): drawn on the photo while the filter is tried
     */
    open val sizeReach: Float = 0.5f

    /** Passed to the shader as uSize (1 = as designed) */
    open val shaderSize: Float get() = 1f

    /** Passed to the shader as uCenter, relative to the image (y down) */
    open val centerX: Float get() = 0.5f
    open val centerY: Float get() = 0.5f

    /**
     * Sets uStrength, uCenter and uSize. Called by the engine before every draw: filters share their
     * compiled shader, so values left by another object of the same filter must be overwritten.
     */
    fun bindSettings(program: ImagineShader.Program) {
        setUniform(program, "uStrength", shaderStrength)
        setUniformVec2(program, "uCenter", centerX, centerY)
        setUniform(program, "uSize", shaderSize)
    }

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

    /** Sets a vec2 uniform of this layer's shader, see [setUniform] */
    protected fun setUniformVec2(program: ImagineShader.Program, name: String, x: Float, y: Float) {
        program.use()
        val location = uniformLocations.getOrPut(program.program to name) {
            GLES20.glGetUniformLocation(program.program, name)
        }
        if (location < 0) return
        GLES20.glUniform2f(location, x, y)
    }

    fun shaderId(): Int {
        return source.hashCode()
    }

    fun resetIntensity(){
        intensity = initialIntensity
    }
}