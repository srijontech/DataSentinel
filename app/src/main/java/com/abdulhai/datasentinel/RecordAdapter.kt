package com.abdulhai.datasentinel

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class RecordAdapter(
    private var items: List<Any>,
    private val onDelete: (MyRecord) -> Unit,
    private val onEdit: (MyRecord) -> Unit,
    private val onHeaderClick: (String) -> Unit,
    private val onShare: (MyRecord) -> Unit // v2.3 Sync
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_RECORD = 1
        private const val TYPE_HEADER = 2
    }

    override fun getItemViewType(position: Int): Int = if (items[position] is MyRecord) TYPE_RECORD else TYPE_HEADER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_RECORD) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
            RecordViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false)
            HeaderViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is RecordViewHolder && item is MyRecord) {
            holder.card.setCardBackgroundColor(Color.parseColor("#93C5CF"))

            holder.category.text = item.category
            holder.subCategory.text = item.subCategory
            holder.content.text = item.content

            holder.btnDelete.setOnClickListener { onDelete(item) }
            holder.btnEdit.setOnClickListener { onEdit(item) }
            holder.btnShare.setOnClickListener { onShare(item) }
        } else if (holder is HeaderViewHolder && item is String) {
            holder.headerText.text = item
            holder.itemView.setOnClickListener { onHeaderClick(item) }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Any>) {
        items = newItems
        notifyDataSetChanged()
    }

    class RecordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.cardView)
        val category: TextView = view.findViewById(R.id.textCategory)
        val subCategory: TextView = view.findViewById(R.id.textSubCategory)
        val content: TextView = view.findViewById(R.id.textContent)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnShare: ImageButton = view.findViewById(R.id.btnShare)
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerText: TextView = view.findViewById(R.id.headerText)
    }
}