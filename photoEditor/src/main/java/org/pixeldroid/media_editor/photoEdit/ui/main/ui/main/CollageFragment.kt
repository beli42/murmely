package org.pixeldroid.media_editor.photoEdit.ui.main.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.databinding.FragmentCollageBinding

class CollageFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()

    companion object {
        const val ACTION_IDENTIFIER = "collageActionIdentifier"
    }

    private lateinit var binding: FragmentCollageBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCollageBinding.inflate(inflater, container, false)
        binding.collage1.setOnClickListener {
            val bundle = Bundle().apply { putInt(ACTION_IDENTIFIER, 1) }
            findNavController().navigate(R.id.action_ChooseCollageFragment_to_ChooseImagesFragment1, bundle)
        }
        binding.collage2.setOnClickListener {
            val bundle = Bundle().apply { putInt(ACTION_IDENTIFIER, 2) }
            findNavController().navigate(R.id.action_ChooseCollageFragment_to_ChooseImagesFragment2, bundle)
        }
        binding.collage3.setOnClickListener {
            val bundle = Bundle().apply { putInt(ACTION_IDENTIFIER, 3) }
            findNavController().navigate(R.id.action_ChooseCollageFragment_to_ChooseImagesFragment3, bundle)
        }
        binding.collage4.setOnClickListener {
            val bundle = Bundle().apply { putInt(ACTION_IDENTIFIER, 4) }
            findNavController().navigate(R.id.action_ChooseCollageFragment_to_ChooseImagesFragment4, bundle)
        }

        return binding.root
    }

}