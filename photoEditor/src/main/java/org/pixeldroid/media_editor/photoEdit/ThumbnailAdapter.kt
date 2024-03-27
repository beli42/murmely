package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import org.pixeldroid.media_editor.photoEdit.databinding.ThumbnailListItemBinding
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class ThumbnailAdapter (private val context: Context,
                        private val tbItemList: List<ImagineLayer?>,
                        private val listener: FilterListFragment,
): RecyclerView.Adapter<ThumbnailAdapter.MyViewHolder>() {

    private var selectedIndex = 0

    var thumbnails: List<Bitmap?> = arrayOfNulls<Bitmap?>(tbItemList.size).toList()
        set(value) {
            field = value
            (context as AppCompatActivity).runOnUiThread {
                notifyDataSetChanged()
            }
        }

    fun resetSelected(){
        listener.onFilterSelected(0)
        val previous = selectedIndex
        selectedIndex = 0
        notifyItemChanged(previous)
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemBinding =
            ThumbnailListItemBinding.inflate(LayoutInflater.from(context), parent, false)

        return MyViewHolder(itemBinding)
    }

    override fun getItemCount(): Int = tbItemList.size

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val tbItem = tbItemList.getOrNull(position)

        holder.thumbnail.setImageBitmap(thumbnails[position])

        holder.thumbnail.setOnClickListener {
            listener.onFilterSelected(position)
            notifyItemChanged(selectedIndex)
            selectedIndex = holder.bindingAdapterPosition
            notifyItemChanged(position)
        }

        holder.filterName.text = context.getString(tbItem?.name ?: R.string.filterNone)

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