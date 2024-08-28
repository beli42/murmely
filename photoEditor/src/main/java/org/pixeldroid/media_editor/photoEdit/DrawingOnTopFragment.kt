package org.pixeldroid.media_editor.photoEdit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import org.pixeldroid.media_editor.photoEdit.databinding.FragmentDrawingBinding

class DrawingOnTopFragment : Fragment() {
    private lateinit var binding: FragmentDrawingBinding
    private lateinit var model: PhotoEditViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentDrawingBinding.inflate(inflater, container, false)

        val _model: PhotoEditViewModel by activityViewModels {
            PhotoEditViewModelFactory()
        }
        model = _model

        binding.buttonDraw.setOnClickListener {
            model.startDraw()
        }
        binding.buttonText.setOnClickListener {
            model.startText()
        }
        binding.buttonStickers.setOnClickListener {
            model.startStickers()
        }

        return binding.root
    }
}
