package com.example.notificationtimerapp

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class TimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var timeLeftMillis: Long = 0

    companion object {
        const val CHANNEL_ID_ALARM = "alarm_channel" // MAX Priority
        const val CHANNEL_ID_TIMER = "timer_channel" // HIGH Priority
        const val CHANNEL_ID_DROP_DOWN = "drop_down_channel" // MAX Priority

        const val URGENT_NOTIFICATION_START_ALARM_ID = 1001
        const val HIGH_NOTIFICATION_RESUME_ID = 1002
        const val HIGH_NOTIFICATION_PAUSE_ID = 1003

        const val ACTION_START_APP = "startApp"
        const val ACTION_STOP_APP = "stopApp"
        const val ACTION_PAUSE_SERVICE = "pauseService"
        const val ACTION_RESUME_SERVICE = "resumeService"
        const val ACTION_START_ALARM = "startAlarm"

        const val EXTRA_DURATION = "duration"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TRACKER","SERVICE STARTED")
        when (intent?.action) {
            ACTION_START_APP -> showStartNotification()
            ACTION_RESUME_SERVICE -> {
                val duration = intent.getLongExtra(EXTRA_DURATION, timeLeftMillis.takeIf { it > 0 } ?: 30000L)
                startTimer(duration)
            }
            ACTION_PAUSE_SERVICE -> pauseTimer()
            ACTION_START_ALARM -> triggerAlarmNotification()
            ACTION_STOP_APP -> stopServiceInternal()
        }
        return START_STICKY
    }

    private fun startTimer(duration: Long) {
        handleDismissingNotification(HIGH_NOTIFICATION_PAUSE_ID)
        handleDismissingNotification(URGENT_NOTIFICATION_START_ALARM_ID)
        timeLeftMillis = duration
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (timeLeftMillis > 0) {
                updateCountdownNotification(timeLeftMillis)
                delay(1000)
                timeLeftMillis -= 1000
            }
            // Transition to Alarm
            handleAction(ACTION_START_ALARM)
        }
    }

    private fun handleDismissingNotification(id : Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(id)
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(HIGH_NOTIFICATION_RESUME_ID)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_TIMER)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("Timer Paused")
            .setContentText("Paused at ${formatTime(timeLeftMillis)}")
            .setOngoing(true)
            .setSilent(true) // Medium/High priority but silent during pause
            .addAction(createAction(ACTION_RESUME_SERVICE, "Resume", timeLeftMillis))
            .addAction(createAction(ACTION_STOP_APP, "Stop"))
            .build()
        manager.notify(HIGH_NOTIFICATION_PAUSE_ID, builder)
        stopSelf()
    }

    private fun triggerAlarmNotification() {
        timerJob?.cancel()
        handleDismissingNotification(HIGH_NOTIFICATION_RESUME_ID)
        val manager = getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time is Up!")
            .setContentText("Timer reached 00:00")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(null, true) // Increases urgency on many devices
            .addAction(createAction(ACTION_RESUME_SERVICE, "Repeat (30s)", 30000L))
            .addAction(createAction(ACTION_STOP_APP, "Stop"))
        manager.notify(URGENT_NOTIFICATION_START_ALARM_ID, builder.build())
        stopSelf()
    }

    private fun showStartNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_DROP_DOWN)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ready to start?")
            .setContentText("Tap start to begin your 30s timer")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(createAction(ACTION_RESUME_SERVICE, "Start", 30000L))
            .build()
        manager.notify(URGENT_NOTIFICATION_START_ALARM_ID, builder)
        stopSelf()
        // no need to start service, just end the service once notification is fired.
    }

    private fun updateCountdownNotification(millis: Long) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_TIMER)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Timer Running")
            .setContentText("Seconds left: ${formatTime(millis)}")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Forces the notification to stay pinned even on Android 14+
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true) // Important: prevents pinging every second
            .addAction(createAction(ACTION_PAUSE_SERVICE, "Pause"))
            .addAction(createAction(ACTION_STOP_APP, "Stop"))
        startForeground(HIGH_NOTIFICATION_RESUME_ID, builder.build())
    }

    // Helper to create PendingIntents for actions
    private fun createAction(actionStr: String, label: String, duration: Long? = null): NotificationCompat.Action {
        val intent = Intent(this, TimerService::class.java).apply {
            action = actionStr
            duration?.let { putExtra(EXTRA_DURATION, it) }
        }
        val pendingIntent = PendingIntent.getService(
            this, actionStr.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(0, label, pendingIntent)
    }

    private fun handleAction(actionStr: String) {
        val intent = Intent(this, TimerService::class.java).apply { action = actionStr }
        startService(intent)
    }

    private fun formatTime(millis: Long): String {
        val sec = (millis / 1000) % 60
        val min = (millis / 60000) % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 1. Alarm Channel: MAX Priority + Sound
            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM, "Alarm Notification", NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Used for start alarms on timer completion"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                // Set the default alarm sound at the channel level
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            val dropDownChannel = NotificationChannel(
                CHANNEL_ID_DROP_DOWN, "Start Timer Notification", NotificationManager.IMPORTANCE_MAX
            ).apply {
                setSound(null, null) // Ensures no sound every second
                enableVibration(false)
                description = "Used for the start timer notification"
            }

            // 2. Timer Channel: High Priority (Visible) but Silent updates
            val timerChannel = NotificationChannel(
                CHANNEL_ID_TIMER, "Ongoing Timer Notification", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null) // Ensures no sound every second
                enableVibration(false)
            }
            manager.createNotificationChannel(alarmChannel)
            manager.createNotificationChannel(timerChannel)
            manager.createNotificationChannel(dropDownChannel)
        }
    }

    private fun stopServiceInternal() {
        timerJob?.cancel()
        handleDismissingNotification(URGENT_NOTIFICATION_START_ALARM_ID)
        handleDismissingNotification(HIGH_NOTIFICATION_PAUSE_ID)
        handleDismissingNotification(HIGH_NOTIFICATION_RESUME_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d("TRACKER","SERVICE ENDED")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}