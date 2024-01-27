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
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.databinding.FragmentFilterListBinding
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.NegativeLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.BlackWhiteLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ContrastedBWLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.MarsLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.TestLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.VignetteLayer

class FilterListFragment : Fragment() {

    private lateinit var binding: FragmentFilterListBinding

    private var listener : ((ImagineLayer?) -> Unit)? = null
    internal lateinit var adapter: ThumbnailAdapter
    private lateinit var tbItemList: MutableList<ImagineLayer?>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentFilterListBinding.inflate(inflater, container, false)

        tbItemList = arrayListOf(null, VignetteLayer(),TestLayer(), MarsLayer(), BlackWhiteLayer(), ContrastedBWLayer(), NegativeLayer())

        binding.recyclerView.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)

        adapter = ThumbnailAdapter(requireActivity(), tbItemList, this)
        binding.recyclerView.adapter = adapter

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        displayImage()
    }

    private fun displayImage() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val tbImage: Bitmap = bitmapFromUri(requireActivity().contentResolver,
                    PhotoEditActivity.imageUri
                )
                setupFilter(tbImage)


//                tbItemList.addAll(ThumbnailsManager.processThumbs(context))
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupFilter(tbImage: Bitmap?) {

//        ThumbnailsManager.clearThumbs()
//        tbItemList.clear()
//
//        val tbItem = ThumbnailItem()
//        tbItem.image = tbImage
//        tbItem.filter.name = getString(R.string.normal_filter)
//        tbItem.filterName = tbItem.filter.name
//        ThumbnailsManager.addThumb(tbItem)
//
//        val filters = FilterPack.getFilterPack(context)
//
//        for (filter in filters) {
//            val item = ThumbnailItem()
//            item.image = tbImage
//            item.filter = filter
//            item.filterName = filter.name
//            ThumbnailsManager.addThumb(item)
//        }
    }

    fun resetSelectedFilter(){
        adapter.resetSelected()
    }

    fun onFilterSelected(filter: ImagineLayer?) {
        listener?.invoke(filter)
    }

    fun setListener(listFragmentListener: (filter: ImagineLayer?) -> Unit) {
        this.listener = listFragmentListener
    }
}
