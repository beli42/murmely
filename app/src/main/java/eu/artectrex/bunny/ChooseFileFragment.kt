package eu.artectrex.bunny

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import eu.artectrex.bunny.databinding.FragmentChooseFileBinding
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.common.PICTURE_POSITION
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.PhotoEditActivity
import org.pixeldroid.media_editor.photoEdit.PhotoEditActivity.Companion.SAVE_TO_FOLDER
import org.pixeldroid.media_editor.photoEdit.PhotoEditActivity.Companion.SAVE_TO_FOLDER_URI
import org.pixeldroid.media_editor.photoEdit.PhotoEditActivity.Companion.SAVE_TO_NEW_FILE
import org.pixeldroid.media_editor.photoEdit.ui.main.CollageActivity
import org.pixeldroid.media_editor.videoEdit.VideoEditActivity

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class ChooseFileFragment : Fragment() {

    private var _binding: FragmentChooseFileBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var model: EditViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChooseFileBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val createVideoContract =
        registerForActivityResult(ActivityResultContracts.CreateDocument("video/*")) { newFileUri ->
            // Save actualFileUri somewhere so the file can be moved there after encoding
            if (newFileUri != null) {
                model.encodeVideoTo(newFileUri)
            } else {
                //TODO error
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val _model: EditViewModel by activityViewModels {
            EditViewModelFactory(
                requireActivity().application,
            )
        }
        model = _model

        binding.buttonFirst.setOnClickListener {
            // Launch the photo picker and let the user choose images and videos.
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
        binding.buttonCollage.setOnClickListener {
            val intent = Intent(context, CollageActivity::class.java)
                // Save collages directly to the folder from the settings
                .putExtra(SAVE_TO_FOLDER, SaveSettings.FOLDER_NAME)
                .putExtra(SAVE_TO_FOLDER_URI, SaveSettings.folderUri(requireContext())?.toString())

            editResultContract.launch(intent)
            model.launchedCollage()
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.uiState.collect { uiState ->
                    uiState.savedMessage?.let { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                        model.savedMessageShown()
                    }
                    uiState.uriToEdit?.let {
                        val type = uiState.type
                        val isVideo = type?.startsWith("video/")

                        val intent = Intent(context,
                            if(isVideo == true) VideoEditActivity::class.java else PhotoEditActivity::class.java
                        )
                            .putExtra(PICTURE_URI, it)
                            .putExtra(PICTURE_POSITION, 0)
                            .putExtra(SAVE_TO_NEW_FILE, true)
                            // Save edited images directly to the folder from the settings
                            .putExtra(SAVE_TO_FOLDER, SaveSettings.FOLDER_NAME)
                            .putExtra(SAVE_TO_FOLDER_URI, SaveSettings.folderUri(requireContext())?.toString())

                        editResultContract.launch(intent)
                        model.launchedEdit()
                    }
                    if (uiState.fragment == 2) findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
                    if (uiState.startVideoEncoding) {
                        //TODO re-use filename from original file here?
                        val prefillFileName = uiState.type?.takeLastWhile { it != '/' }?.let {
                            "edited.$it"
                        } ?: ""
                        createVideoContract.launch(prefillFileName)
                        model.launchedVideoCreation()
                    }
                }
            }
        }
    }

    private val editResultContract: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()){
            result: ActivityResult? ->
        if (result?.resultCode == Activity.RESULT_OK && result.data != null) {
            // The edit returned successfully, you can get the results from the intent data:
            model.editResult(result.data!!)
        } else if(result?.resultCode != Activity.RESULT_CANCELED){
            Toast.makeText(requireContext(), "Error while editing", Toast.LENGTH_SHORT).show()
        }
    }

    // Registers a photo picker activity launcher in single-select mode.
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        // Callback is invoked after the user selects a media item or closes the photo picker.
        // Use url in callback to give it in Intent to edit activity
        model.filePickResult(uri)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}