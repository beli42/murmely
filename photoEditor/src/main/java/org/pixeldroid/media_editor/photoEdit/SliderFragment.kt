package org.pixeldroid.media_editor.photoEdit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnChangeListener
import kotlinx.coroutines.flow.StateFlow
import org.pixeldroid.media_editor.photoEdit.databinding.FragmentEditImageBinding

class SliderFragment : Fragment(), OnChangeListener {

    private lateinit var binding: FragmentEditImageBinding

    private lateinit var model: PhotoEditViewModel

    /**
     * Describes one adjustment slider: its range, how to read its value from [PhotoEditViewModel.Sliders]
     * and which [PhotoEditViewModel] function to call when it changes.
     */
    private class SliderSpec(
        val slider: Slider,
        val min: Float,
        val max: Float,
        val get: (PhotoEditViewModel.Sliders) -> Float,
        val onChange: (Float) -> Unit,
    )

    private lateinit var specs: List<SliderSpec>

    companion object {
        const val BRIGHTNESS_MAX = .004f
        const val CONTRAST_MAX= .9f
        const val SATURATION_MAX = 1f
        const val BRIGHTNESS_MIN = -.004f
        const val CONTRAST_MIN= -.9f
        const val SATURATION_MIN = -1f

        const val SHARPNESS_MIN = -1f
        const val SHARPNESS_MAX = 1f
        const val HIGHLIGHTS_MIN = -1f
        const val HIGHLIGHTS_MAX = 1f
        const val SHADOWS_MIN = -1f
        const val SHADOWS_MAX = 1f
        const val WARMTH_MIN = -1f
        const val WARMTH_MAX = 1f
        const val TINT_MIN = -1f
        const val TINT_MAX = 1f
        const val VIGNETTE_MIN = -1f
        const val VIGNETTE_MAX = 1f
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentEditImageBinding.inflate(inflater, container, false)

        val _model: PhotoEditViewModel by activityViewModels {
            PhotoEditViewModelFactory()
        }
        model = _model

        specs = listOf(
            SliderSpec(binding.sliderBrightness, BRIGHTNESS_MIN, BRIGHTNESS_MAX, { it.brightness }, model::onBrightnessChange),
            SliderSpec(binding.sliderContrast, CONTRAST_MIN, CONTRAST_MAX, { it.contrast }, model::onContrastChange),
            SliderSpec(binding.sliderSaturation, SATURATION_MIN, SATURATION_MAX, { it.saturation }, model::onSaturationChange),
            SliderSpec(binding.sliderHighlights, HIGHLIGHTS_MIN, HIGHLIGHTS_MAX, { it.highlights }, model::onHighlightsChange),
            SliderSpec(binding.sliderShadows, SHADOWS_MIN, SHADOWS_MAX, { it.shadows }, model::onShadowsChange),
            SliderSpec(binding.sliderWarmth, WARMTH_MIN, WARMTH_MAX, { it.warmth }, model::onWarmthChange),
            SliderSpec(binding.sliderTint, TINT_MIN, TINT_MAX, { it.tint }, model::onTintChange),
            SliderSpec(binding.sliderSharpness, SHARPNESS_MIN, SHARPNESS_MAX, { it.sharpness }, model::onSharpnessChange),
            SliderSpec(binding.sliderVignette, VIGNETTE_MIN, VIGNETTE_MAX, { it.vignette }, model::onVignetteChange),
        )

        specs.forEach { spec ->
            spec.slider.valueFrom = spec.min
            spec.slider.valueTo = spec.max
            spec.slider.value = spec.get(model.sliders.value)
            spec.slider.addOnChangeListener(this)
        }

        return binding.root
    }

    fun resetControl(sliders: StateFlow<PhotoEditViewModel.Sliders>? = null) {
        val values = sliders?.value ?: PhotoEditViewModel.Sliders()
        specs.forEach { it.slider.value = it.get(values) }
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        specs.firstOrNull { it.slider == slider }?.onChange?.invoke(value)
    }
}
