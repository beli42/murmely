package org.pixeldroid.media_editor.photoEdit

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnChangeListener
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.photoEdit.databinding.FragmentEditImageBinding

class SliderFragment : Fragment(),  OnChangeListener {

    private lateinit var binding: FragmentEditImageBinding

    private lateinit var model: PhotoEditViewModel

    private var BRIGHTNESS_MAX = 1f
    private var CONTRAST_MAX= 9f
    private var SATURATION_MAX = 10f
    private var BRIGHTNESS_MIN = -1f
    private var CONTRAST_MIN= -9f
    private var SATURATION_MIN = -10f

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

        binding.sliderBrightness.valueTo = BRIGHTNESS_MAX
        binding.sliderBrightness.valueFrom = BRIGHTNESS_MIN
        binding.sliderBrightness.value = model.sliders.value.brightness

        binding.sliderContrast.valueTo = CONTRAST_MAX
        binding.sliderContrast.valueFrom = CONTRAST_MIN
        binding.sliderContrast.value = model.sliders.value.contrast

        binding.sliderSaturation.valueTo = SATURATION_MAX
        binding.sliderSaturation.valueFrom = SATURATION_MIN
        binding.sliderSaturation.value = model.sliders.value.saturation

        setOnSliderChangeListeners(this)

        return binding.root
    }

    private fun setOnSliderChangeListeners(listener: OnChangeListener){
            binding.sliderBrightness.addOnChangeListener(listener)
            binding.sliderContrast.addOnChangeListener(listener)
            binding.sliderSaturation.addOnChangeListener(listener)
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        when (slider) {
            binding.sliderBrightness -> model.onBrightnessChange(.004f * value)
            binding.sliderContrast -> model.onContrastChange(.10f * value)
            binding.sliderSaturation -> model.onSaturationChange(.10f * value)
        }
    }
}
