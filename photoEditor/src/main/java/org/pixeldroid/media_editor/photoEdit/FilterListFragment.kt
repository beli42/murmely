package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.common.dpToPx
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomFilter
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomLayer
import org.pixeldroid.media_editor.photoEdit.customFilters.DatabaseBuilder
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

class FilterListFragment: Fragment() {
    private lateinit var binding: FragmentFilterListBinding

    private var listener: ((ImagineLayer?) -> Unit)? = null
    private lateinit var adapter: ThumbnailAdapter

    private val predefinedLayers: List<ImagineLayer> = arrayListOf(
        ElsaLayer(),
        VintageLayer(),
        MarsLayer(),
        FrostLayer(),
        SepiaLayer(),
        BlackWhiteLayer(),
        RandLayer(),
        NegativeLayer()
    )

    private var customLayers: List<CustomLayer> = emptyList()

    private val tbItemList: List<ImagineLayer>
        get() = predefinedLayers + customLayers


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

        val db = DatabaseBuilder.getInstance(requireContext())

        val customLayer =
            CustomFilter("Custom 1", """
        vec4 process(vec4 color, sampler2D uImage, vec2 vTexCoords) {
            return vec4(vec3(color.r * 0.3 + color.g * 0.59 + color.b * 0.11), color.a);
        }
    """.trimIndent())
        db.filtersDao().insertAll(customLayer)
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.filtersDao().getAll().flowOn(Dispatchers.IO)
                    .collect { filters: List<CustomFilter> ->
                        customLayers = filters.map { it.toLayer() }
                        adapter.tbItemList = tbItemList + filters.map { it.toLayer() }
                        imagineEngine.layers = tbItemList + filters.map { it.toLayer() }
                        imagineEngine.exportBitmap(true, 50.dpToPx(requireContext()))
                    }
            }
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
