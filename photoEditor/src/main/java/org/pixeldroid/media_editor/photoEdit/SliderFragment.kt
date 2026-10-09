package org.pixeldroid.media_editor.photoEdit

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
     * Describes one adjustment slider: its views, range, how to read its value from
     * [PhotoEditViewModel.Sliders] and which [PhotoEditViewModel] function to call when it changes.
     * The neutral value of every slider is 0.
     */
    private class SliderSpec(
        val slider: Slider,
        val label: TextView,
        val valueText: TextView,
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
        const val EXPOSURE_MIN = -1f
        const val EXPOSURE_MAX = 1f
        const val VIBRANCE_MIN = -1f
        const val VIBRANCE_MAX = 1f
        const val CLARITY_MIN = -1f
        const val CLARITY_MAX = 1f
        // Grain only makes sense in one direction
        const val GRAIN_MIN = 0f
        const val GRAIN_MAX = 1f
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

        with(binding) {
            specs = listOf(
                SliderSpec(sliderExposure, labelExposure, valueExposure, EXPOSURE_MIN, EXPOSURE_MAX, { it.exposure }, model::onExposureChange),
                SliderSpec(sliderBrightness, labelBrightness, valueBrightness, BRIGHTNESS_MIN, BRIGHTNESS_MAX, { it.brightness }, model::onBrightnessChange),
                SliderSpec(sliderContrast, labelContrast, valueContrast, CONTRAST_MIN, CONTRAST_MAX, { it.contrast }, model::onContrastChange),
                SliderSpec(sliderHighlights, labelHighlights, valueHighlights, HIGHLIGHTS_MIN, HIGHLIGHTS_MAX, { it.highlights }, model::onHighlightsChange),
                SliderSpec(sliderShadows, labelShadows, valueShadows, SHADOWS_MIN, SHADOWS_MAX, { it.shadows }, model::onShadowsChange),
                SliderSpec(sliderClarity, labelClarity, valueClarity, CLARITY_MIN, CLARITY_MAX, { it.clarity }, model::onClarityChange),
                SliderSpec(sliderSaturation, labelSaturation, valueSaturation, SATURATION_MIN, SATURATION_MAX, { it.saturation }, model::onSaturationChange),
                SliderSpec(sliderVibrance, labelVibrance, valueVibrance, VIBRANCE_MIN, VIBRANCE_MAX, { it.vibrance }, model::onVibranceChange),
                SliderSpec(sliderWarmth, labelWarmth, valueWarmth, WARMTH_MIN, WARMTH_MAX, { it.warmth }, model::onWarmthChange),
                SliderSpec(sliderTint, labelTint, valueTint, TINT_MIN, TINT_MAX, { it.tint }, model::onTintChange),
                SliderSpec(sliderSharpness, labelSharpness, valueSharpness, SHARPNESS_MIN, SHARPNESS_MAX, { it.sharpness }, model::onSharpnessChange),
                SliderSpec(sliderVignette, labelVignette, valueVignette, VIGNETTE_MIN, VIGNETTE_MAX, { it.vignette }, model::onVignetteChange),
                SliderSpec(sliderGrain, labelGrain, valueGrain, GRAIN_MIN, GRAIN_MAX, { it.grain }, model::onGrainChange),
            )
        }

        specs.forEach { spec ->
            spec.slider.valueFrom = spec.min
            spec.slider.valueTo = spec.max
            spec.slider.value = spec.get(model.sliders.value)
            spec.slider.addOnChangeListener(this)
            updateValueText(spec)
            setupTouchHandling(spec)
        }

        return binding.root
    }

    /**
     * Touch handling for a slider inside the scrolling list:
     * - Double-tapping the slider (or tapping its value) resets it to zero.
     * - The slider does not take focus on touch. (It stays focusable for keyboard /
     *   accessibility navigation.) Scrolling vs. dragging is left to the Material Slider,
     *   which already handles being inside a scrolling container, and automatic scrolling
     *   of the list is prevented by [StableNestedScrollView].
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchHandling(spec: SliderSpec) {
        val slider = spec.slider
        slider.isFocusableInTouchMode = false

        // Set when the current gesture is the second tap of a double tap: the slider must
        // not see it, otherwise it jumps back to the touch position after the reset.
        var consumeGesture = false

        val detector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                consumeGesture = true
                slider.value = 0f
                return true
            }
        })

        slider.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            val consume = consumeGesture
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                consumeGesture = false
            }
            // true = the slider doesn't receive the event
            consume
        }
        spec.valueText.setOnClickListener { slider.value = 0f }
        spec.valueText.contentDescription = getString(R.string.reset_slider, spec.label.text)
    }

    /**
     * Shows the slider value as a percentage of its range, e.g. "+35", "−20" or "0".
     */
    private fun updateValueText(spec: SliderSpec) {
        spec.valueText.text = SliderValueFormat.label(
            SliderValueFormat.percent(spec.slider.value, spec.min, spec.max)
        )
    }

    fun resetControl(sliders: StateFlow<PhotoEditViewModel.Sliders>? = null) {
        if (!::specs.isInitialized) return
        val values = sliders?.value ?: PhotoEditViewModel.Sliders()
        specs.forEach { it.slider.value = it.get(values) }
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        val spec = specs.firstOrNull { it.slider == slider } ?: return
        spec.onChange(value)
        updateValueText(spec)
    }
}
