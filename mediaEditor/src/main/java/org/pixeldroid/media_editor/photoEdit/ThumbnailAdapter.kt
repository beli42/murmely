package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.pixeldroid.media_editor.R
import org.pixeldroid.media_editor.databinding.ThumbnailListItemBinding
import org.pixeldroid.media_editor.photoEdit.imagine.UriImageProvider
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineEngine
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineView
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import java.io.File
import kotlin.concurrent.thread

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
        selectedIndex = 0
        listener.onFilterSelected(0)
        //notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemBinding =
            ThumbnailListItemBinding.inflate(LayoutInflater.from(context), parent, false)

        return MyViewHolder(itemBinding)
    }

    override fun getItemCount(): Int = tbItemList.size

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val tbItem = tbItemList.getOrNull(position)
        if(position == 0) {
            Log.e("zeroooo", "wassup")
        }

        holder.thumbnail.setImageBitmap(thumbnails[position])

        holder.thumbnail.setOnClickListener {
            listener.onFilterSelected(position)
            selectedIndex = holder.bindingAdapterPosition
        }

        holder.filterName.text = tbItem?.name ?: "None" // TODO: extract strings

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