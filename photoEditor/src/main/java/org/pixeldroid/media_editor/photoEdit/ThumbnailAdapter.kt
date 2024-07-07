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
                        initialFilters: List<ImagineLayer?>,
                        private val listener: FilterListFragment,
): RecyclerView.Adapter<ThumbnailAdapter.MyViewHolder>() {

    private var selectedIndex = 0

    var tbItemList: List<ImagineLayer?> = initialFilters
        set(value) {
            field = value
            (context as AppCompatActivity).runOnUiThread {
                notifyDataSetChanged()
            }
        }

    var thumbnails: List<Bitmap?> = arrayOfNulls<Bitmap?>(initialFilters.size).toList()
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

    override fun getItemCount(): Int = tbItemList.size + 1

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        // Last item in list is a button to add a new custom filter
        if (position == itemCount - 1) {
            holder.thumbnail.setImageResource(R.drawable.add)
            holder.filterName.text = "Add Custom Filter"
            holder.filterName.setTextColor(context.getColorFromAttr(R.attr.colorOnBackground))

            holder.thumbnail.setOnClickListener {
                listener.onFilterSelected(position)
            }
            return
        }
        val tbItem = tbItemList.getOrNull(position)

        holder.thumbnail.setImageBitmap(thumbnails.getOrNull(position))

        holder.thumbnail.setOnClickListener {
            listener.onFilterSelected(position)
            notifyItemChanged(selectedIndex)
            selectedIndex = holder.bindingAdapterPosition
            notifyItemChanged(position)
        }
        holder.thumbnail.setOnLongClickListener {
            listener.onFilterSelected(position, longClick = true)
            true
        }

        holder.filterName.text =
                // If the item is null, this is a noop item ("None" filter)
            if (tbItem == null) context.getString(R.string.filterNone)
            else if (tbItem.name != null) context.getString(tbItem.name!!)
            else if (tbItem.customName != null) tbItem.customName
            else throw IllegalArgumentException()

        if (selectedIndex == position)
            holder.filterName.setTextColor(context.getColorFromAttr(R.attr.colorPrimary))
        else
            holder.filterName.setTextColor(context.getColorFromAttr(R.attr.colorOnBackground))
    }

    class MyViewHolder(itemBinding: ThumbnailListItemBinding): RecyclerView.ViewHolder(itemBinding.root) {
        var thumbnail: ImageView = itemBinding.thumbnail
        var filterName: TextView = itemBinding.filterName
    }
}