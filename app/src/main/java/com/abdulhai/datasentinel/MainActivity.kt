package com.abdulhai.datasentinel

import android.app.AlertDialog
import android.content.Intent
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

    // 0=Default, 1=Category List, 2=Sub-Category List
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

        adapter = RecordAdapter(listOf(),
            onDelete = { r -> confirmAction("Delete entry?") { lifecycleScope.launch { db.recordDao().deleteRecord(r); loadData() } } },
            onEdit = { r -> confirmAction("Edit entry?") { startActivity(Intent(this, InputActivity::class.java).putExtra("RECORD_ID", r.id)) } },
            onHeaderClick = { name -> handleDrillDown(name) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // SEARCH: Filters Category, Sub-Category, AND Content
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

        // DRAWER SORTING OPTIONS
        val nav = binding.navView
        nav.findViewById<Button>(R.id.menuDefault).setOnClickListener {
            currentMode = 0
            loadData()
            binding.drawerLayout.closeDrawers()
        }
        nav.findViewById<Button>(R.id.menuCategoryView).setOnClickListener {
            currentMode = 1
            loadData()
            binding.drawerLayout.closeDrawers()
        }
        nav.findViewById<Button>(R.id.menuSubCategoryView).setOnClickListener {
            currentMode = 2
            loadData()
            binding.drawerLayout.closeDrawers()
        }

        // TOOL BUTTONS
        nav.findViewById<Button>(R.id.menuBackup).setOnClickListener { showBackupDialog() }
        nav.findViewById<Button>(R.id.menuImport).setOnClickListener { confirmAction("Import CSV?") { triggerImport() } }
        nav.findViewById<Button>(R.id.menuExport).setOnClickListener { confirmAction("Export CSV?") { exportToCsv() } }
        nav.findViewById<Button>(R.id.menuTimeout).setOnClickListener { showTimeoutDialog() }
        nav.findViewById<Button>(R.id.menuLogout).setOnClickListener { finish() }

        binding.fabAdd.setOnClickListener { startActivity(Intent(this, InputActivity::class.java)) }
        loadPreferences()
    }

    private fun loadData() {
        lifecycleScope.launch {
            allRecords = db.recordDao().getAllRecords()
            when(currentMode) {
                0 -> {
                    adapter.updateData(allRecords)
                    supportActionBar?.title = "Data Sentinel"
                }
                1 -> {
                    val categories = allRecords.map { it.category }.distinct().sorted()
                    adapter.updateData(categories)
                    supportActionBar?.title = "By Category"
                }
                2 -> {
                    val subCategories = allRecords.map { it.subCategory }.distinct().sorted()
                    adapter.updateData(subCategories)
                    supportActionBar?.title = "By Sub-Category"
                }
            }
        }
    }

    private fun handleDrillDown(header: String) {
        val filtered = if (currentMode == 1) {
            allRecords.filter { it.category == header }
        } else {
            allRecords.filter { it.subCategory == header }
        }
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
                allRecords.forEach { out.append("${it.category},${it.subCategory},${it.content}\n") }

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
                        withContext(Dispatchers.IO) {
                            val reader = BufferedReader(InputStreamReader(contentResolver.openInputStream(uri)))
                            reader.readLine() // Skip header
                            reader.forEachLine { line ->
                                val parts = line.split(",")
                                if (parts.size >= 3) {
                                    val record = MyRecord(0, parts[0], parts[1], parts[2])
                                    // Use a non-suspending way or a direct call here if inside IO
                                    launch { db.recordDao().insertRecord(record) }
                                }
                            }
                        }
                        loadData()
                        Toast.makeText(this@MainActivity, "Imported Successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) { Toast.makeText(this@MainActivity, "Import failed", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    // --- SECURITY & PREFS ---

    private fun authenticateUser() {
        // 1. Ensure the overlay is visible before the prompt appears
        binding.blurOverlay.visibility = View.VISIBLE

        val biometrics = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // 2. AUTH SUCCESS: Remove the haze so the user can see the entries
                binding.blurOverlay.visibility = View.GONE
                resetTimer()
                loadData()
            }

            override fun onAuthenticationError(e: Int, s: CharSequence) {
                // 3. AUTH ERROR: Keep the haze visible and close app
                binding.blurOverlay.visibility = View.VISIBLE
                finish()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Keep hazy
            }
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
        AlertDialog.Builder(this)
            .setTitle("Set Backup Reminder")
            .setItems(options) { _, which ->
                val interval = if (which == 0) "Weekly" else "Monthly"

                // Save the choice and the current time to SharedPreferences
                getSharedPreferences("DS_Prefs", MODE_PRIVATE).edit()
                    .putString("backup_interval", interval)
                    .putLong("last_backup_time", System.currentTimeMillis())
                    .apply()

                Toast.makeText(this, "Backup reminder set to $interval", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // Add this function to check if it's "Reminding Day"
    private fun checkBackupStatus() {
        val prefs = getSharedPreferences("DS_Prefs", MODE_PRIVATE)
        val lastBackup = prefs.getLong("last_backup_time", 0L)
        val interval = prefs.getString("backup_interval", "Weekly")

        if (lastBackup == 0L) return

        val currentTime = System.currentTimeMillis()
        val oneWeek = 7L * 24 * 60 * 60 * 1000
        val oneMonth = 30L * 24 * 60 * 60 * 1000

        val limit = if (interval == "Weekly") oneWeek else oneMonth

        if (currentTime - lastBackup > limit) {
            AlertDialog.Builder(this)
                .setTitle("Backup Required")
                .setMessage("It has been over a $interval since your last backup. Would you like to export your data now?")
                .setPositiveButton("Export Now") { _, _ -> exportToCsv() }
                .setNegativeButton("Later", null)
                .show()
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