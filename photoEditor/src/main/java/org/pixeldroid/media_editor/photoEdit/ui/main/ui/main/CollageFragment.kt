package org.pixeldroid.media_editor.photoEdit.ui.main.ui.main

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.databinding.FragmentCollageBinding

class CollageFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var binding: FragmentCollageBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCollageBinding.inflate(inflater, container, false)
        binding.collage.setOnClickListener {
            findNavController().navigate(R.id.action_ChooseCollageFragment_to_ChooseImagesFragment)
        }
        return binding.root
    }

}