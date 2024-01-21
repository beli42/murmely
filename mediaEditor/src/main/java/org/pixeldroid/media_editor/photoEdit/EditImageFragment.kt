package org.pixeldroid.media_editor.photoEdit

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnChangeListener
import org.pixeldroid.media_editor.databinding.FragmentEditImageBinding

class EditImageFragment : Fragment(),  Slider.OnChangeListener {

    private var listener: PhotoEditActivity? = null
    private lateinit var binding: FragmentEditImageBinding

    private var BRIGHTNESS_MAX = 1f
    private var CONTRAST_MAX= 9f
    private var SATURATION_MAX = 20f
    private var BRIGHTNESS_MIN = -1f
    private var CONTRAST_MIN= -9f
    private var SATURATION_MIN = -1f
    private var BRIGHTNESS_START = 0f
    private var SATURATION_START = 0f
    private var CONTRAST_START = 0f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentEditImageBinding.inflate(inflater, container, false)

        binding.sliderBrightness.valueTo = BRIGHTNESS_MAX
        binding.sliderBrightness.valueFrom = BRIGHTNESS_MIN
        binding.sliderBrightness.value = BRIGHTNESS_START

        binding.sliderContrast.valueTo = CONTRAST_MAX
        binding.sliderContrast.valueFrom = CONTRAST_MIN
        binding.sliderContrast.value = CONTRAST_START

        binding.sliderSaturation.valueTo = SATURATION_MAX
        binding.sliderSaturation.value = SATURATION_START

        setOnSliderChangeListeners(this)

        return binding.root
    }

    private fun setOnSliderChangeListeners(listener: OnChangeListener){
            binding.sliderBrightness.addOnChangeListener(listener)
            binding.sliderContrast.addOnChangeListener(listener)
            binding.sliderSaturation.addOnChangeListener(listener)
    }

    fun resetControl() {
        // Make sure to ignore seekbar change events, since we don't want to have the reset cause
        // filter applications due to the onProgressChanged calls
        binding.sliderBrightness.removeOnChangeListener(this)
        binding.sliderBrightness.value = BRIGHTNESS_START
        binding.sliderContrast.value = CONTRAST_START
        binding.sliderSaturation.value = SATURATION_START
        setOnSliderChangeListeners(this)
    }

    fun setListener(listener: PhotoEditActivity) {
        this.listener = listener
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        var prog = value

        listener?.let {
            when(slider) {
                binding.sliderBrightness -> it.onBrightnessChange(.004f * value)
                binding.sliderContrast -> it.onContrastChange(.10f * prog)
                binding.sliderSaturation -> {
                    prog += 10
                    it.onSaturationChange(.10f * prog)
                }
            }
        }
    }
}
