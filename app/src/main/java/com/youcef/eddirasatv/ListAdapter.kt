package com.youcef.eddirasatv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ListAdapter(
    private val items: List<NavItem>,
    private val onClick: (NavItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var focusRequested = false
    private val initialFocusPosition: Int = items.indexOfFirst { !it.isHeader }

    override fun getItemViewType(position: Int): Int =
        if (items[position].isHeader) TYPE_HEADER else TYPE_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_header, parent, false))
        } else {
            RowHolder(inflater.inflate(R.layout.item_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderHolder) {
            holder.title.text = item.title
            return
        }
        holder as RowHolder
        holder.title.text = item.title
        if (item.subtitle.isNullOrBlank()) {
            holder.subtitle.visibility = View.GONE
        } else {
            holder.subtitle.visibility = View.VISIBLE
            holder.subtitle.text = item.subtitle
        }
        holder.chevron.visibility = if (item.pdfUrl != null) View.GONE else View.VISIBLE
        holder.itemView.setOnClickListener { onClick(item) }

        if (!focusRequested && position == initialFocusPosition) {
            focusRequested = true
            holder.itemView.post { holder.itemView.requestFocus() }
        }
    }

    override fun getItemCount(): Int = items.size

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.rowTitle)
        val subtitle: TextView = view.findViewById(R.id.rowSubtitle)
        val chevron: TextView = view.findViewById(R.id.rowChevron)
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.headerTitle)
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ROW = 1
    }
}
