package com.abdulhai.datasentinel

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.abdulhai.datasentinel.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordAdapter
    private lateinit var db: AppDatabase
    private var allRecords: List<MyRecord> = listOf()
    private var isAuthenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Initialize Database
        db = AppDatabase.getDatabase(this)

        // 2. Setup RecyclerView with Edit/Delete Actions
        adapter = RecordAdapter(listOf(),
            onDelete = { record -> deleteEntry(record) },
            onEdit = { record -> editEntry(record) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // 3. Setup Export Button
        binding.btnExport.setOnClickListener {
            if (allRecords.isNotEmpty()) {
                exportToExcel()
            } else {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Setup Biometrics
        setupBiometrics()

        // 5. Setup Search Logic
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterData(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 6. FAB to Add New Entry
        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, InputActivity::class.java)
            startActivity(intent)
        }

        binding.textFooter.text = "Developed By: Abdul Hai Bhuyan, AGM, BD&R, AVG"
    }

    private fun setupBiometrics() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isAuthenticated = true
                loadDataFromDatabase()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(this@MainActivity, "Authentication required", Toast.LENGTH_SHORT).show()
                // You can call finish() here if you want to close the app on cancel
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Data Sentinel Login")
            .setSubtitle("Use fingerprint or face to access data")
            .setNegativeButtonText("Exit")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun loadDataFromDatabase() {
        lifecycleScope.launch {
            allRecords = db.recordDao().getAllRecords()
            adapter.updateData(allRecords)
        }
    }

    private fun deleteEntry(record: MyRecord) {
        lifecycleScope.launch {
            db.recordDao().deleteRecord(record)
            loadDataFromDatabase()
            Toast.makeText(this@MainActivity, "Entry Deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun editEntry(record: MyRecord) {
        val intent = Intent(this, InputActivity::class.java)
        intent.putExtra("RECORD_ID", record.id)
        startActivity(intent)
    }

    private fun filterData(query: String) {
        val filteredList = allRecords.filter {
            it.category.contains(query, ignoreCase = true) ||
                    it.subCategory.contains(query, ignoreCase = true) ||
                    it.content.contains(query, ignoreCase = true)
        }
        adapter.updateData(filteredList)
    }

    private fun exportToExcel() {
        // Create CSV Content
        val csvHeader = "Category,Sub-category,Content\n"
        val csvRows = allRecords.joinToString("\n") {
            // Escape commas in content to prevent breaking CSV columns
            val safeContent = it.content.replace(",", " ")
            "${it.category},${it.subCategory},$safeContent"
        }
        val fullContent = csvHeader + csvRows

        try {
            // Save file to app cache
            val file = File(cacheDir, "Data_Sentinel_Export.csv")
            file.writeText(fullContent)

            // Get secure URI using FileProvider
            val contentUri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )

            // Share Intent
            val exportIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(exportIntent, "Export to Excel"))

        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAuthenticated) {
            loadDataFromDatabase()
        }
    }
}