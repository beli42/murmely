package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.pixeldroid.media_editor.R
import org.pixeldroid.media_editor.databinding.ThumbnailListItemBinding
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class ThumbnailAdapter (private val context: Context,
                        private val tbItemList: List<ImagineLayer?>,
                        private val listener: FilterListFragment
): RecyclerView.Adapter<ThumbnailAdapter.MyViewHolder>() {

    private var selectedIndex = 0

    fun resetSelected(){
        selectedIndex = 0
        listener.onFilterSelected(null)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemBinding = ThumbnailListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyViewHolder(itemBinding)
    }

    override fun getItemCount(): Int {
        return tbItemList.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val tbItem = tbItemList[position]
        Glide.with(context).load(PhotoEditActivity.imageUri).into(holder.thumbnail)
        // TODO apply filter
        holder.thumbnail.setOnClickListener {
            listener.onFilterSelected(tbItem)
            selectedIndex = holder.bindingAdapterPosition
            notifyDataSetChanged()
        }

        holder.filterName.text = tbItem?.name ?: "None" // TODO: extract string

        if(selectedIndex == position)
            holder.filterName.setTextColor(context.getColorFromAttr(R.attr.colorPrimary))
        else
            holder.filterName.setTextColor(context.getColorFromAttr(R.attr.colorOnBackground))
    }

    class MyViewHolder(itemBinding: ThumbnailListItemBinding): RecyclerView.ViewHolder(itemBinding.root) {
        var thumbnail: ImageView = itemBinding.thumbnail
        var filterName: TextView = itemBinding.filterName
    }
}