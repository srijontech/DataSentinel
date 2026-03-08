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

        // Play alert sound
        playMultipleBeeps()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sentinel_reminders"

        // Create Channel with High Importance for Pop-up behavior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Critical entry reminders"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }

        // Prepare intent to launch MainActivity and trigger the popup
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("POPUP_CONTENT", content)
            putExtra("POPUP_TITLE", title)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, id, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true) // Forces the heads-up pop
            .setAutoCancel(true)
            .build()

        manager.notify(id, notification)
    }

    private fun playMultipleBeeps() {
        val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        val handler = Handler(Looper.getMainLooper())

        val beepRunnable = object : Runnable {
            var count = 0
            override fun run() {
                if (count < 10) {
                    toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 200)
                    count++
                    handler.postDelayed(this, 1000)
                } else {
                    toneGen.release()
                }
            }
        }
        handler.post(beepRunnable)
    }
}