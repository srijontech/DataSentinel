package com.abdulhai.datasentinel

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abdulhai.datasentinel.databinding.ActivityInputBinding
import kotlinx.coroutines.launch

class InputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInputBinding
    private lateinit var db: AppDatabase
    private var recordId: Int = 0 // 0 means new entry, otherwise editing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        // Check if we are editing an existing record
        recordId = intent.getIntExtra("RECORD_ID", 0)
        if (recordId != 0) {
            loadExistingRecord()
            binding.btnSave.text = "Update Entry"
        }

        binding.btnSave.setOnClickListener {
            saveData()
        }
    }

    private fun loadExistingRecord() {
        lifecycleScope.launch {
            val record = db.recordDao().getRecordById(recordId)
            record?.let {
                binding.etCategory.setText(it.category)
                binding.etSubCategory.setText(it.subCategory)
                binding.etContent.setText(it.content)
            }
        }
    }

    private fun saveData() {
        val cat = binding.etCategory.text.toString().trim()
        val sub = binding.etSubCategory.text.toString().trim()
        val content = binding.etContent.text.toString().trim()

        if (cat.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Please fill Category and Content", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val newRecord = MyRecord(
                id = if (recordId == 0) 0 else recordId,
                category = cat,
                subCategory = sub,
                content = content
            )

            db.recordDao().insertRecord(newRecord)

            // This toast confirms success before closing
            Toast.makeText(this@InputActivity, "Data Saved Successfully", Toast.LENGTH_SHORT).show()

            // Closing this activity sends user back to MainActivity,
            // where onResume() will immediately show the new data.
            finish()
        }
    }
}