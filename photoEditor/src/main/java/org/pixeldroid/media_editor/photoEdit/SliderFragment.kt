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
import org.pixeldroid.media_editor.photoEdit.PhotoEditViewModel.Sliders.Companion.BRIGHTNESS_START
import org.pixeldroid.media_editor.photoEdit.PhotoEditViewModel.Sliders.Companion.CONTRAST_START
import org.pixeldroid.media_editor.photoEdit.PhotoEditViewModel.Sliders.Companion.SATURATION_START
import org.pixeldroid.media_editor.photoEdit.databinding.FragmentEditImageBinding

class SliderFragment : Fragment(), OnChangeListener {

    private lateinit var binding: FragmentEditImageBinding

    private lateinit var model: PhotoEditViewModel

    companion object {
        const val BRIGHTNESS_MAX = .004f
        const val CONTRAST_MAX= .9f
        const val SATURATION_MAX = 1f
        const val BRIGHTNESS_MIN = -.004f
        const val CONTRAST_MIN= -.9f
        const val SATURATION_MIN = -1f

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

    fun resetControl(sliders: StateFlow<PhotoEditViewModel.Sliders>? = null) {
        binding.sliderBrightness.value = sliders?.value?.brightness ?: BRIGHTNESS_START
        binding.sliderContrast.value = sliders?.value?.contrast ?: CONTRAST_START
        binding.sliderSaturation.value = sliders?.value?.saturation ?: SATURATION_START
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        when (slider) {
            binding.sliderBrightness -> model.onBrightnessChange(value)
            binding.sliderContrast -> model.onContrastChange(value)
            binding.sliderSaturation -> model.onSaturationChange(value)
        }
    }
}
