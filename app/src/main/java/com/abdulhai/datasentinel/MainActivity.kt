package com.abdulhai.datasentinel

import android.app.AlertDialog
import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.abdulhai.datasentinel.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordAdapter
    private lateinit var db: AppDatabase
    private var allRecords: List<MyRecord> = listOf()

    private var currentMode = 0
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val logoutRunnable = Runnable { finish() }
    private var selectedTimeout = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = AppDatabase.getDatabase(this)
        setupUI()
        authenticateUser()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, 0, 0)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Updated for v2.4: Added the 6th parameter for Long Click
        adapter = RecordAdapter(listOf(),
            onDelete = { r -> confirmAction("Delete entry?") { lifecycleScope.launch { db.recordDao().deleteRecord(r); loadData() } } },
            onEdit = { r -> confirmAction("Edit entry?") { startActivity(Intent(this, InputActivity::class.java).putExtra("RECORD_ID", r.id)) } },
            onHeaderClick = { name -> handleDrillDown(name) },
            onShare = { r -> shareRecord(r) },
            onLongClick = { r -> showCopyDialog(r) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().lowercase()
                val filtered = allRecords.filter {
                    it.category.lowercase().contains(q) || it.subCategory.lowercase().contains(q) || it.content.lowercase().contains(q)
                }
                adapter.updateData(filtered)
            }
            override fun beforeTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        val nav = binding.navView
        nav.findViewById<Button>(R.id.menuDefault).setOnClickListener { currentMode = 0; loadData(); binding.drawerLayout.closeDrawers() }
        nav.findViewById<Button>(R.id.menuCategoryView).setOnClickListener { currentMode = 1; loadData(); binding.drawerLayout.closeDrawers() }
        nav.findViewById<Button>(R.id.menuSubCategoryView).setOnClickListener { currentMode = 2; loadData(); binding.drawerLayout.closeDrawers() }
        nav.findViewById<Button>(R.id.menuBackup).setOnClickListener { showBackupDialog() }
        nav.findViewById<Button>(R.id.menuImport).setOnClickListener { confirmAction("Import CSV?") { triggerImport() } }
        nav.findViewById<Button>(R.id.menuExport).setOnClickListener { confirmAction("Export CSV?") { exportToCsv() } }
        nav.findViewById<Button>(R.id.menuTimeout).setOnClickListener { showTimeoutDialog() }
        nav.findViewById<Button>(R.id.menuLogout).setOnClickListener { finish() }

        binding.fabAdd.setOnClickListener { startActivity(Intent(this, InputActivity::class.java)) }
        loadPreferences()
    }

    // --- NEW IN v2.4: Copy Dialog ---
    private fun showCopyDialog(record: MyRecord) {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Copy Content")) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Sentinel Content", record.content)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun shareRecord(record: MyRecord) {
        val shareBody = "Category: ${record.category}\nSub: ${record.subCategory}\n\n${record.content}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun loadData() {
        lifecycleScope.launch {
            allRecords = db.recordDao().getAllRecords()
            when(currentMode) {
                0 -> { adapter.updateData(allRecords); supportActionBar?.title = "Data Sentinel" }
                1 -> { adapter.updateData(allRecords.map { it.category }.distinct().sorted()); supportActionBar?.title = "By Category" }
                2 -> { adapter.updateData(allRecords.map { it.subCategory }.distinct().sorted()); supportActionBar?.title = "By Sub-Category" }
            }
        }
    }

    private fun handleDrillDown(header: String) {
        val filtered = if (currentMode == 1) allRecords.filter { it.category == header } else allRecords.filter { it.subCategory == header }
        currentMode = 0
        adapter.updateData(filtered)
        supportActionBar?.title = header
    }

    private fun exportToCsv() {
        lifecycleScope.launch {
            try {
                val file = File(getExternalFilesDir(null), "DataSentinel_Backup.csv")
                val out = StringBuilder()
                out.append("Category,SubCategory,Content\n")
                allRecords.forEach {
                    val safeContent = it.content.replace("\n", "[BR]").replace("\r", "[BR]")
                    out.append("${it.category},${it.subCategory},$safeContent\n")
                }
                withContext(Dispatchers.IO) { file.writeText(out.toString()) }
                val uri = FileProvider.getUriForFile(this@MainActivity, "${packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share Data"))
            } catch (e: Exception) { Toast.makeText(this@MainActivity, "Export failed", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun triggerImport() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "text/*" }
        startActivityForResult(intent, 99)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 99 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                lifecycleScope.launch {
                    try {
                        val newRecords = mutableListOf<MyRecord>()
                        withContext(Dispatchers.IO) {
                            val existingRecords = db.recordDao().getAllRecords()
                            val inputStream = contentResolver.openInputStream(uri)
                            val reader = BufferedReader(InputStreamReader(inputStream))
                            reader.readLine()
                            reader.forEachLine { line ->
                                val parts = line.split(",", limit = 3)
                                if (parts.size >= 3) {
                                    val cat = parts[0].trim()
                                    val sub = parts[1].trim()
                                    val con = parts[2].trim().replace("[BR]", "\n")
                                    if (cat.isNotEmpty()) {
                                        val isDuplicate = existingRecords.any {
                                            it.category.equals(cat, ignoreCase = true) && it.subCategory.equals(sub, ignoreCase = true)
                                        }
                                        if (!isDuplicate) newRecords.add(MyRecord(0, cat, sub, con))
                                    }
                                }
                            }
                            inputStream?.close()
                            newRecords.forEach { db.recordDao().insertRecord(it) }
                        }
                        loadData()
                        Toast.makeText(this@MainActivity, "Imported ${newRecords.size} records", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) { Toast.makeText(this@MainActivity, "Import failed", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    private fun authenticateUser() {
        binding.blurOverlay.visibility = View.VISIBLE
        val biometrics = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                binding.blurOverlay.visibility = View.GONE
                resetTimer()
                loadData()
            }
            override fun onAuthenticationError(e: Int, s: CharSequence) { finish() }
            override fun onAuthenticationFailed() {}
        })
        biometrics.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Login Required").setNegativeButtonText("Exit").build())
    }

    private fun resetTimer() {
        timeoutHandler.removeCallbacks(logoutRunnable)
        timeoutHandler.postDelayed(logoutRunnable, (selectedTimeout * 60 * 1000).toLong())
    }

    override fun onUserInteraction() { super.onUserInteraction(); resetTimer() }

    private fun showBackupDialog() {
        val options = arrayOf("Weekly", "Monthly")
        AlertDialog.Builder(this).setTitle("Set Backup Reminder").setItems(options) { _, which ->
            val interval = if (which == 0) "Weekly" else "Monthly"
            getSharedPreferences("DS_Prefs", MODE_PRIVATE).edit()
                .putString("backup_interval", interval)
                .putLong("last_backup_time", System.currentTimeMillis()).apply()
            Toast.makeText(this, "Backup reminder set to $interval", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun checkBackupStatus() {
        val prefs = getSharedPreferences("DS_Prefs", MODE_PRIVATE)
        val last = prefs.getLong("last_backup_time", 0L)
        val interval = prefs.getString("backup_interval", "Weekly")
        if (last == 0L) return
        val limit = if (interval == "Weekly") 7L * 24 * 60 * 60 * 1000 else 30L * 24 * 60 * 60 * 1000
        if (System.currentTimeMillis() - last > limit) {
            AlertDialog.Builder(this).setTitle("Backup Required").setMessage("Export now?")
                .setPositiveButton("Export Now") { _, _ -> exportToCsv() }.setNegativeButton("Later", null).show()
        }
    }

    private fun showTimeoutDialog() {
        val opt = arrayOf("1 Min", "2 Min", "5 Min")
        AlertDialog.Builder(this).setTitle("Auto-Logout").setItems(opt) { _, i ->
            selectedTimeout = when(i) { 0 -> 1; 1 -> 2; else -> 5 }
            getSharedPreferences("DS_Prefs", MODE_PRIVATE).edit().putInt("timeout", selectedTimeout).apply()
            resetTimer()
        }.show()
    }

    private fun loadPreferences() { selectedTimeout = getSharedPreferences("DS_Prefs", MODE_PRIVATE).getInt("timeout", 1) }

    private fun confirmAction(msg: String, action: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("Yes") { _, _ -> action() }.setNegativeButton("No", null).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean { menuInflater.inflate(R.menu.main_menu, menu); return true }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_export) confirmAction("Export?") { exportToCsv() }
        if (item.itemId == R.id.action_import) confirmAction("Import?") { triggerImport() }
        return super.onOptionsItemSelected(item)
    }
}