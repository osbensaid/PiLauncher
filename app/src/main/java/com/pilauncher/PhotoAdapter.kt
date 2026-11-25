package com.pilauncher

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/** Grid of photo thumbnails for choosing a background. */
class PhotoAdapter(
    private val photos: List<Uri>,
    private val onPick: (Uri) -> Unit,
) : RecyclerView.Adapter<PhotoAdapter.Holder>() {

    private val loader = Executors.newFixedThreadPool(2)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val tile: View = view.findViewById(R.id.photoTile)
        val image: ImageView = view.findViewById(R.id.photo)
    }

    override fun getItemCount() = photos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        val holder = Holder(view)
        holder.tile.outlineProvider = ViewOutlineProvider.BACKGROUND
        holder.tile.clipToOutline = true
        holder.tile.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onPick(photos[pos])
        }
        holder.tile.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val uri = photos[position]
        holder.image.setImageDrawable(null)
        holder.image.tag = uri
        val resolver = holder.image.context.contentResolver
        loader.execute {
            val thumb: Bitmap? = try {
                resolver.loadThumbnail(uri, Size(320, 180), null)
            } catch (e: Exception) {
                null
            }
            holder.image.post {
                if (holder.image.tag == uri) holder.image.setImageBitmap(thumb)
            }
        }
    }

    fun shutdown() = loader.shutdownNow()
}
