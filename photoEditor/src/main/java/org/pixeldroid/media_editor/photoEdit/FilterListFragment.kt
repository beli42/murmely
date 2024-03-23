package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import org.pixeldroid.media_editor.common.dpToPx
import org.pixeldroid.media_editor.photoEdit.databinding.FragmentFilterListBinding
import org.pixeldroid.media_editor.photoEdit.imagine.UriImageProvider
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineEngine
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.BlackWhiteLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ElsaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.FrostLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.MarsLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.NegativeLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.RandLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SepiaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.VintageLayer

class FilterListFragment : Fragment() {

    private lateinit var binding: FragmentFilterListBinding

    private var listener: ((ImagineLayer?) -> Unit)? = null
    private lateinit var adapter: ThumbnailAdapter
    private val tbItemList: List<ImagineLayer> = arrayListOf(
        ElsaLayer(),
        VintageLayer(),
        MarsLayer(),
        FrostLayer(),
        SepiaLayer(),
        BlackWhiteLayer(),
        RandLayer(),
        NegativeLayer()
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentFilterListBinding.inflate(inflater, container, false)

        binding.recyclerView.layoutManager =
            LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)

        adapter = ThumbnailAdapter(requireActivity(), listOf(null) + tbItemList, this)
        binding.recyclerView.adapter = adapter

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val imagineEngine = ImagineEngine(binding.thumbnailImagine).apply {
            imageProvider =
                PhotoEditActivity.imageUri?.let { UriImageProvider(requireContext(), it) }
        }
        imagineEngine.layers = tbItemList

        imagineEngine.onThumbnails = { list: List<Bitmap> ->
            adapter.thumbnails = list
        }
        imagineEngine.exportBitmap(true, 50.dpToPx(requireContext()))
    }

    fun resetSelectedFilter() {
        adapter.resetSelected()
    }

    fun onFilterSelected(index: Int) {
        listener?.invoke(tbItemList.getOrNull(index - 1))
    }

    fun setListener(listFragmentListener: (filter: ImagineLayer?) -> Unit) {
        this.listener = listFragmentListener
    }
}
