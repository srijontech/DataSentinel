package com.abdulhai.datasentinel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("TITLE") ?: "Reminder"
        val content = intent.getStringExtra("CONTENT") ?: "Data Sentinel Task"
        val id = intent.getIntExtra("ID", 0)

        // 1. Play the short beep multiple beep 10 times
        playMultipleBeeps()

        // 2. Show the Notification with Full-Screen Intent
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sentinel_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        // This intent points to MainActivity to "Pop Up" the app
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("POPUP_CONTENT", content) // Pass the specific content to show
            putExtra("POPUP_TITLE", title)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, id, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Use MAX for pop-up
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true) // <--- THIS MAKES IT POP UP
            .setAutoCancel(true)
            .build()

        manager.notify(id, notification)
    }

    // Function moved here to be accessible by onReceive
    private fun playMultipleBeeps() {
        // Uses the ALARM stream so it's heard even if media is muted
        val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        val handler = Handler(Looper.getMainLooper())

        val beepRunnable = object : Runnable {
            var count = 0
            override fun run() {
                if (count < 10) { // <--- 10 TIMES
                    // TONE_CDMA_PIP is a sharp, clear beep perfect for alerts
                    toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 200)
                    count++

                    // 1 second interval between beeps
                    handler.postDelayed(this, 1000)
                } else {
                    toneGen.release() // Clean up memory after 10th beep
                }
            }
        }
        handler.post(beepRunnable)
    }

    // Keeping your previous logic intact as requested, though it is now unused
    private fun playTripleBeep() {
        val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        val handler = Handler(Looper.getMainLooper())

        val beepRunnable = object : Runnable {
            var count = 0
            override fun run() {
                if (count < 3) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                    count++
                    handler.postDelayed(this, 600)
                } else {
                    toneGen.release()
                }
            }
        }
        handler.post(beepRunnable)
    }
}