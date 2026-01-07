package com.abdulhai.datasentinel

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abdulhai.datasentinel.databinding.ActivityInputBinding
import kotlinx.coroutines.launch

class InputActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInputBinding
    private lateinit var db: AppDatabase
    private var editingRecordId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInputBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = AppDatabase.getDatabase(this)

        // Check if we are editing an existing record
        editingRecordId = intent.getIntExtra("RECORD_ID", 0)
        if (editingRecordId != 0) {
            loadRecordForEditing(editingRecordId)
        }

        setupCategoryAutoComplete()
        binding.btnSave.setOnClickListener { saveRecord() }
    }

    private fun loadRecordForEditing(id: Int) {
        lifecycleScope.launch {
            val record = db.recordDao().getRecordById(id)
            record?.let {
                binding.editCategory.setText(it.category)
                binding.editSubCategory.setText(it.subCategory)
                binding.editContent.setText(it.content)
            }
        }
    }

    private fun setupCategoryAutoComplete() {
        lifecycleScope.launch {
            val categories = db.recordDao().getAllRecords().map { it.category }.distinct()
            val adapter = ArrayAdapter(this@InputActivity, android.R.layout.simple_dropdown_item_1line, categories)
            binding.editCategory.setAdapter(adapter)
        }
    }

    private fun saveRecord() {
        val cat = binding.editCategory.text.toString().trim()
        val subCat = binding.editSubCategory.text.toString().trim()
        val cont = binding.editContent.text.toString().trim()

        if (cat.isEmpty() || cont.isEmpty()) {
            Toast.makeText(this, "Fill Category and Content", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val record = MyRecord(id = editingRecordId, category = cat, subCategory = subCat, content = cont)
            db.recordDao().insertRecord(record)
            finish()
        }
    }
}