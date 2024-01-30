package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
                        thumbnailImagineView: ImagineView
): RecyclerView.Adapter<ThumbnailAdapter.MyViewHolder>() {

    private var selectedIndex = 0

    private var imagineEngine = ImagineEngine(thumbnailImagineView).apply {
        imageProvider = PhotoEditActivity.imageUri?.let { UriImageProvider(context, it) } }

    private val thumbnails: Array<Bitmap?> = arrayOfNulls(tbItemList.size)

    fun resetSelected(){
        selectedIndex = 0
        listener.onFilterSelected(null)
        //notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemBinding =
            ThumbnailListItemBinding.inflate(LayoutInflater.from(context), parent, false)

        return MyViewHolder(itemBinding)
    }

    override fun getItemCount(): Int = tbItemList.size

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        //holder.setIsRecyclable(false)
        val tbItem = tbItemList[position]

        if(position == 0) {
            Glide.with(context).load(PhotoEditActivity.imageUri).into(holder.thumbnail)
        } else if (thumbnails[position] == null){
            imagineEngine.layers = if (tbItem == null) emptyList() else listOf(tbItem)

            imagineEngine.onBitmap = { bitmap ->
                thumbnails[position] = bitmap
                (context as AppCompatActivity).runOnUiThread {
                    notifyDataSetChanged()
                }
            }
            imagineEngine.exportBitmap(true, (50).dpToPx(context))
            holder.thumbnail.setImageBitmap(null)
        } else {
            holder.thumbnail.setImageBitmap(thumbnails[position])
        }

        holder.thumbnail.setOnClickListener {
            listener.onFilterSelected(tbItem)
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