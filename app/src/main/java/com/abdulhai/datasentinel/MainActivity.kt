package com.abdulhai.datasentinel

import android.app.*
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
import androidx.activity.result.contract.ActivityResultContracts
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
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordAdapter
    private lateinit var db: AppDatabase
    private var allRecords: List<MyRecord> = listOf()

    private var currentMode = 0
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val logoutRunnable = Runnable { finish() }
    private var selectedTimeout = 1

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                processImport(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        setupUI()
        authenticateUser()
    }

    // CRITICAL: Handles the data when the app is already open in the background
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Replaces the old intent with the new one containing the reminder data
        checkIntentForPopup()
    }

    override fun onResume() {
        super.onResume()
        loadData()
        checkBackupStatus()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, 0, 0)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        adapter = RecordAdapter(listOf(),
            onDelete = { r -> confirmAction("Delete entry?") {
                lifecycleScope.launch {
                    db.recordDao().deleteRecord(r)
                    loadData()
                }
            } },
            onEdit = { r -> confirmAction("Edit entry?") { startActivity(Intent(this, InputActivity::class.java).putExtra("RECORD_ID", r.id)) } },
            onHeaderClick = { name -> handleDrillDown(name) },
            onShare = { r -> shareRecord(r) },
            onLongClick = { r -> showCopyDialog(r) },
            onReminderClick = { r -> showReminderDialog(r) }
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

    // Logic to show the entry details in a dialog when triggered by a reminder
    private fun checkIntentForPopup() {
        val title = intent.getStringExtra("POPUP_TITLE")
        val content = intent.getStringExtra("POPUP_CONTENT")

        if (content != null) {
            // Prevent popup from appearing over the biometric login screen
            if (binding.blurOverlay.visibility == View.VISIBLE) return

            AlertDialog.Builder(this)
                .setTitle(title ?: "Reminder Alert")
                .setMessage(content)
                .setCancelable(false)
                .setPositiveButton("Dismiss") { dialog, _ ->
                    // Clear extras to prevent the dialog from reappearing on rotation
                    intent.removeExtra("POPUP_CONTENT")
                    intent.removeExtra("POPUP_TITLE")
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun showReminderDialog(record: MyRecord) {
        AlertDialog.Builder(this)
            .setTitle("Remind You Later?")
            .setMessage("Would you like to schedule a notification for this entry?")
            .setPositiveButton("Yes") { _, _ -> pickTimeForReminder(record) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun pickTimeForReminder(record: MyRecord) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(this, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    Toast.makeText(this, "Please pick a future time", Toast.LENGTH_SHORT).show()
                } else {
                    scheduleNotification(record, calendar.timeInMillis)
                }
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun scheduleNotification(record: MyRecord, timeInMillis: Long) {
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("TITLE", "${record.category}: ${record.subCategory}")
            putExtra("CONTENT", record.content)
            putExtra("ID", record.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, record.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                return
            }
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        Toast.makeText(this, "Reminder set!", Toast.LENGTH_LONG).show()
    }

    private fun showCopyDialog(record: MyRecord) {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Copy Content")) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Sentinel Content", record.content)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
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
                getSharedPreferences("DS_Prefs", MODE_PRIVATE).edit()
                    .putLong("last_backup_time", System.currentTimeMillis()).apply()
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
        importLauncher.launch(intent)
    }

    private fun processImport(uri: android.net.Uri) {
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
                Toast.makeText(this@MainActivity, "Imported!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@MainActivity, "Import failed", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun authenticateUser() {
        binding.blurOverlay.visibility = View.VISIBLE
        val biometrics = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                binding.blurOverlay.visibility = View.GONE
                resetTimer()
                loadData()
                checkIntentForPopup() // Check if we opened the app via a notification
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
        val options = arrayOf("Daily", "Weekly", "Monthly", "Half-Yearly", "Not Required")
        AlertDialog.Builder(this)
            .setTitle("Set Backup Reminder")
            .setItems(options) { _, which ->
                val interval = options[which]
                getSharedPreferences("DS_Prefs", MODE_PRIVATE).edit()
                    .putString("backup_interval", interval)
                    .putLong("last_backup_time", System.currentTimeMillis())
                    .apply()
                Toast.makeText(this, "Reminder set to $interval", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun checkBackupStatus() {
        val prefs = getSharedPreferences("DS_Prefs", MODE_PRIVATE)
        val last = prefs.getLong("last_backup_time", 0L)
        val lastSnoozed = prefs.getLong("last_snooze_time", 0L)
        val interval = prefs.getString("backup_interval", "Weekly")
        if (last == 0L || interval == "Not Required") return
        val oneDay = 24L * 60 * 60 * 1000
        if (System.currentTimeMillis() - lastSnoozed < oneDay) return
        val limit = when (interval) {
            "Daily" -> oneDay
            "Weekly" -> 7 * oneDay
            "Monthly" -> 30 * oneDay
            "Half-Yearly" -> 182 * oneDay
            else -> 7 * oneDay
        }
        if (System.currentTimeMillis() - last > limit) {
            AlertDialog.Builder(this)
                .setTitle("Backup Reminder")
                .setMessage("Your $interval backup is due.")
                .setPositiveButton("Export Now") { _, _ -> exportToCsv() }
                .setNegativeButton("Later") { _, _ ->
                    prefs.edit().putLong("last_snooze_time", System.currentTimeMillis()).apply()
                }
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