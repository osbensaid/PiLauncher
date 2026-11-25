package com.pilauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
    private val onFocused: (AppEntry) -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    val items = mutableListOf<AppEntry>()

    /** When true every app uses the icon style, so all tiles look the same. */
    var iconsOnly = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** Package of the app being moved, or null when not in move mode. */
    var movingPackage: String? = null
        private set

    init {
        setHasStableIds(true)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val tile: View = view.findViewById(R.id.tile)
        val banner: ImageView = view.findViewById(R.id.banner)
        val iconBox: View = view.findViewById(R.id.iconBox)
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
        val moveBorder: View = view.findViewById(R.id.moveBorder)
    }

    fun submit(apps: List<AppEntry>) {
        items.clear()
        items.addAll(apps)
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int) {
        val app = items.removeAt(from)
        items.add(to, app)
        notifyItemMoved(from, to)
    }

    fun setMoving(pkg: String?) {
        val old = movingPackage
        movingPackage = pkg
        listOfNotNull(old, pkg).forEach { p ->
            val index = items.indexOfFirst { it.packageName == p }
            if (index >= 0) notifyItemChanged(index, PAYLOAD_MOVE)
        }
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int) = items[position].packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        val holder = Holder(view)

        holder.tile.outlineProvider = ViewOutlineProvider.BACKGROUND
        holder.tile.clipToOutline = true

        // Icon fills most of the tile, with even space around it
        holder.tile.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val pad = (v.height * 0.18f).toInt()
            if (holder.icon.paddingTop != pad) holder.icon.setPadding(pad, pad, pad, pad)
        }

        holder.tile.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(pos)
        }
        holder.tile.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onLongClick(pos)
            true
        }
        holder.tile.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.12f else 1f
            v.animate().scaleX(scale).scaleY(scale)
                .translationZ(if (hasFocus) 24f else 0f)
                .setDuration(160).start()
            holder.label.animate().alpha(if (hasFocus) 1f else 0f).setDuration(160).start()
            val pos = holder.bindingAdapterPosition
            if (hasFocus && pos != RecyclerView.NO_POSITION) onFocused(items[pos])
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            bindMoveState(holder, items[position])
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        if (app.banner != null && !iconsOnly) {
            holder.banner.setImageDrawable(app.banner)
            holder.banner.visibility = View.VISIBLE
            holder.iconBox.visibility = View.GONE
        } else {
            holder.banner.visibility = View.GONE
            holder.iconBox.visibility = View.VISIBLE
            holder.iconBox.setBackgroundColor(app.tileColor)
            holder.icon.setImageDrawable(app.icon)
        }
        holder.label.text = app.label
        holder.label.alpha = if (holder.tile.isFocused) 1f else 0f
        bindMoveState(holder, app)
    }

    private fun bindMoveState(holder: Holder, app: AppEntry) {
        holder.moveBorder.visibility =
            if (app.packageName == movingPackage) View.VISIBLE else View.GONE
    }

    companion object {
        private const val PAYLOAD_MOVE = "move"
    }
}
