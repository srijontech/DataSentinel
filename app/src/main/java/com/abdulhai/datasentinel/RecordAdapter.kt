package com.abdulhai.datasentinel

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abdulhai.datasentinel.databinding.ItemRecordBinding

class RecordAdapter(
    private var records: List<MyRecord>,
    private val onDelete: (MyRecord) -> Unit,
    private val onEdit: (MyRecord) -> Unit
) : RecyclerView.Adapter<RecordAdapter.RecordViewHolder>() {

    class RecordViewHolder(val binding: ItemRecordBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = records[position]
        holder.binding.textCategory.text = record.category
        holder.binding.textSubCategory.text = record.subCategory
        holder.binding.textContent.text = record.content

        // Setup click listeners for the icons
        holder.binding.btnDelete.setOnClickListener { onDelete(record) }
        holder.binding.btnEdit.setOnClickListener { onEdit(record) }
    }

    override fun getItemCount() = records.size

    fun updateData(newRecords: List<MyRecord>) {
        this.records = newRecords
        notifyDataSetChanged()
    }
}