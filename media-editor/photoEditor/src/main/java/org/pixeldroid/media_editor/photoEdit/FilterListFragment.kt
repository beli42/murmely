package org.pixeldroid.media_editor.photoEdit

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.common.dpToPx
import org.pixeldroid.media_editor.photoEdit.LogViewActivity.Companion.launchLogView
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomFilter
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomLayer
import org.pixeldroid.media_editor.photoEdit.customFilters.DatabaseBuilder
import org.pixeldroid.media_editor.photoEdit.databinding.DialogCustomFilterBinding
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

    private lateinit var adapter: ThumbnailAdapter

    private lateinit var model: PhotoEditViewModel

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

        val _model: PhotoEditViewModel by activityViewModels {
            PhotoEditViewModelFactory()
        }
        model = _model

        val orientation = resources.configuration.orientation
        binding.recyclerView.layoutManager = (if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            GridLayoutManager(activity, 2)
        } else LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false))

        adapter = ThumbnailAdapter(requireActivity(), listOf(null) + tbItemList, this)
        binding.recyclerView.adapter = adapter

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val thumbnailImagineEngine = ImagineEngine(binding.thumbnailImagine).apply {
            imageProvider =
                model.imageUri?.let { UriImageProvider(requireContext(), it) }
        }
        val onThumbnails = { list: List<Bitmap?> ->
            adapter.thumbnails = list
            if (list.any {it == null}) (context as? AppCompatActivity)?.runOnUiThread {
                Snackbar.make(
                    binding.root, R.string.filter_error, Snackbar.LENGTH_LONG
                ).setAction(R.string.view_log, launchLogView(requireContext())).show()
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val db = DatabaseBuilder.getInstance(requireContext())
                //FIXME remove this horrible hack if race condition/init problem fixed
                do {
                    db.filtersDao().getAll().firstOrNull()?.let { filters ->
                        customLayers = filters.map { it.toLayer() }
                        adapter.tbItemList = listOf(null) + tbItemList
                        thumbnailImagineEngine.exportBitmap(
                            true,
                            50.dpToPx(requireContext()),
                            tbItemList,
                            onThumbnails
                        )
                    }
                    delay(300)
                } while (adapter.thumbnails.all { it == null })
                db.filtersDao().getAll().distinctUntilChanged().drop(1)
                    .collect { filters: List<CustomFilter> ->
                        customLayers = filters.map { it.toLayer() }
                        adapter.tbItemList = listOf(null) + tbItemList
                        thumbnailImagineEngine.exportBitmap(
                            true,
                            50.dpToPx(requireContext()),
                            tbItemList,
                            onThumbnails
                        )
                    }
            }
        }
    }

    fun resetSelectedFilter(filter: ImagineLayer? = null, manual: Boolean = false) {
        adapter.resetSelected(filter, manual)
    }

    enum class FilterType {
        New, Edit, ViewOnly
    }

    fun onFilterSelected(index: Int, longClick: Boolean = false) {
        // If last item in the list, show a dialog to add a custom filter
        if (index == tbItemList.size + 1) {
            showCustomFilterDialog(modifyLayer = null, FilterType.New)
        } else if (longClick) {
            // Long click, modify the corresponding (if any) custom filter or show built-in one
            val layer = tbItemList.getOrNull(index - 1)
            showCustomFilterDialog(layer,
                if (layer is CustomLayer) FilterType.Edit
                else FilterType.ViewOnly
            )
        }
        // In other cases, just invoke the listener
        else model.doChange(PhotoEditViewModel.Change.SelectFilter(tbItemList.getOrNull(index - 1)))
    }

    private fun EditText.setReadOnly(originalText: CharSequence) {
        // Hack to make sure the callback isn't triggered by itself changing the text
        var changed = false
        val textWatcher: TextWatcher = doAfterTextChanged {
            @Suppress("KotlinConstantConditions")
            if (changed) return@doAfterTextChanged
            changed = true
            setText(originalText)
            changed = false
        }
        addTextChangedListener(textWatcher)
    }

    private fun showCustomFilterDialog(modifyLayer: ImagineLayer?, type: FilterType) {
        if(type != FilterType.New && modifyLayer == null) return

        val customFilterBinding = DialogCustomFilterBinding.inflate(layoutInflater)

        modifyLayer?.let {
            val name = when (type) {
                FilterType.ViewOnly -> it.name?.let { id -> getString(id) } ?: ""
                else -> it.customName
            }
            customFilterBinding.customFilterTitle.editText?.setText(name)
            customFilterBinding.customFilterShader.editText?.setText(it.source)
        }

        if (type == FilterType.ViewOnly){
            customFilterBinding.customFilterTitleEditText.setReadOnly(getText(modifyLayer?.name!!))
            customFilterBinding.customFilterShaderEditText.setReadOnly(modifyLayer.source)
        }

        val dialogBuilder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                getString(
                    when (type) {
                        FilterType.New -> R.string.create_custom_filter
                        FilterType.Edit -> R.string.edit_custom_filter
                        FilterType.ViewOnly -> R.string.view_filter
                    }
                )
            )
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val title =
                    customFilterBinding.customFilterTitle.editText?.text?.toString()?.ifEmpty {
                        getString(
                            R.string.custom_filter
                        )
                    }
                        ?: getString(R.string.custom_filter)
                val code = customFilterBinding.customFilterShader.editText?.text?.toString() ?: ""

                when (type) {
                    FilterType.ViewOnly -> return@setPositiveButton
                    else -> {
                        val db = DatabaseBuilder.getInstance(requireContext())
                        lifecycleScope.launch(Dispatchers.IO) {
                            (modifyLayer as? CustomLayer).let {
                                db.filtersDao().insertOrUpdate(
                                    CustomFilter(title, code, it?.uid ?: 0)
                                )
                            }
                        }
                    }
                }
            }
        if (type != FilterType.ViewOnly) {
            dialogBuilder.setNeutralButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
        }

        if (type == FilterType.Edit) {
            dialogBuilder.setNegativeButtonIcon(
                AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.delete
                )
            )
                .setNegativeButton(R.string.delete) { _, _ ->
                    val db = DatabaseBuilder.getInstance(requireContext())
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.filtersDao().delete((modifyLayer as CustomLayer).toFilter())
                    }
                }
        }
        dialogBuilder.setView(customFilterBinding.root).show()
    }
}
