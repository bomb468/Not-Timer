package com.example.notificationtimerapp

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.notificationtimerapp.MyApplication.Companion.CHANNEL_ID_ALARM
import com.example.notificationtimerapp.MyApplication.Companion.CHANNEL_ID_DROP_DOWN
import com.example.notificationtimerapp.MyApplication.Companion.CHANNEL_ID_TIMER

class TimerReceiver : BroadcastReceiver() {
    companion object{
        const val URGENT_NOTIFICATION_START_ALARM_ID = 1001
        const val HIGH_NOTIFICATION_ID = 1002

        const val ACTION_START_APP = "startApp"
        const val ACTION_STOP_APP = "stopApp"
        const val ACTION_PAUSE_SERVICE = "pauseService"
        const val ACTION_RESUME_SERVICE = "resumeService"
        const val ACTION_START_ALARM = "startAlarm"

        const val EXTRA_DURATION = "duration"
        const val EXTRA_ALARM_IN = "alarm_in"
    }
    override fun onReceive(context: Context, intent: Intent) {
        // Log.d("TRACKER","Service Received")
        when(intent.action) {
            ACTION_START_APP -> {
                // building notification
                val manager = context.getSystemService(NotificationManager::class.java)
                val builder = NotificationCompat.Builder(context, CHANNEL_ID_DROP_DOWN)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Ready to start?")
                    .setContentText("Tap start to begin your 30s timer")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .addAction(provideEventAction(context, ACTION_RESUME_SERVICE, 30_000L))
                    .build()
                manager.notify(URGENT_NOTIFICATION_START_ALARM_ID,builder)
            }
            ACTION_STOP_APP -> {
                cancelAlarm(context)
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.cancel(HIGH_NOTIFICATION_ID)
                manager.cancel(URGENT_NOTIFICATION_START_ALARM_ID)
            }
            ACTION_PAUSE_SERVICE -> {
                cancelAlarm(context)
                val alarmIn = intent.getLongExtra(EXTRA_ALARM_IN, 30_000L)
                val manager = context.getSystemService(NotificationManager::class.java)
                val remaining = alarmIn - SystemClock.elapsedRealtime()
                // building notification
                val builder = NotificationCompat.Builder(context, CHANNEL_ID_TIMER)
                    .setSmallIcon(android.R.drawable.ic_media_pause)
                    .setContentTitle("Timer Paused")
                    .setContentText("Paused at ${formatTime(remaining)}")
                    .setOngoing(true)
                    .setSilent(true) // Medium/High priority but silent during pause
                    .addAction(provideEventAction(context, ACTION_RESUME_SERVICE, remaining, "Resume"))
                    .addAction(provideEventAction(context, ACTION_STOP_APP))
                    .build()
                manager.notify(HIGH_NOTIFICATION_ID,builder)
            }
            ACTION_RESUME_SERVICE -> {
                // get duration from extras
                val duration = intent.getLongExtra(EXTRA_DURATION, 30_000L)
                val manager = context.getSystemService(NotificationManager::class.java)
                // cancel previous notifications if present
                manager.cancel(URGENT_NOTIFICATION_START_ALARM_ID)
                // Calculate the absolute system time when the timer will hit zero
                val stopTime = SystemClock.elapsedRealtime() + duration
                val wallClockStopTime = System.currentTimeMillis() + duration
                // build notification
                val builder = NotificationCompat.Builder(context, CHANNEL_ID_TIMER)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("Timer Running")
                    .setContentText("Countdown in progress")
                    .setWhen(wallClockStopTime)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setOngoing(true)
                    .setShowWhen(true)
                    .setSilent(true)
                    .addAction(provideEventAction(context, ACTION_PAUSE_SERVICE, stopTime))
                    .addAction(provideEventAction(context, ACTION_STOP_APP))
                    .build()
                manager.notify(HIGH_NOTIFICATION_ID,builder)
                // triggers alarm manager at exact time and start receiver
                val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
                val alarmActionIntent = Intent(context, TimerReceiver::class.java).apply {
                    action = ACTION_START_ALARM
                }
                val pendingIntentForAlarmEvent = PendingIntent.getBroadcast(
                    context,
                    0,
                    alarmActionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                // Use exact alarm to ensure it hits precisely at 0 seconds
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    stopTime,
                    pendingIntentForAlarmEvent
                )
            }
            ACTION_START_ALARM -> {
                // cancel previous notifications if any
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.cancel(HIGH_NOTIFICATION_ID)
                // build notification
                val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Time is Up!")
                    .setContentText("Timer reached 00:00")
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOngoing(true)
                    //.setFullScreenIntent(null, true) // Increases urgency on many devices
                    .addAction(provideEventAction(context,ACTION_RESUME_SERVICE, 30_000L, "Repeat"))
                    .addAction(provideEventAction(context,ACTION_STOP_APP))
                manager.notify(URGENT_NOTIFICATION_START_ALARM_ID,builder.build())
            }
        }
        // Log.d("TRACKER","Service Ended")
    }
    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TimerReceiver::class.java)
        intent.action=ACTION_START_ALARM
        // The PendingIntent must match the one used in startTimer exactly
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0, // Match the request code used earlier
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // This stops the TimerReceiver from ever firing
        alarmManager.cancel(pendingIntent)
    }
    private fun provideEventAction(context : Context, action : String, stopTime : Long? = null, label : String? = null) : NotificationCompat.Action {
        when(action){
            ACTION_RESUME_SERVICE -> {
                // build resume service Intent
                val resumeActionIntent = Intent(context, TimerReceiver::class.java)
                resumeActionIntent.action = ACTION_RESUME_SERVICE
                resumeActionIntent.putExtra(EXTRA_DURATION, stopTime ?: 30_000L)
                val pendingIntentForResumeEvent = PendingIntent.getBroadcast(
                    context, ACTION_RESUME_SERVICE.hashCode(), resumeActionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                return if (label != null){
                    NotificationCompat.Action(0, label, pendingIntentForResumeEvent)
                }else{
                    NotificationCompat.Action(0, "Start", pendingIntentForResumeEvent)
                }

            }
            ACTION_PAUSE_SERVICE -> {
                // build pause service Intent
                val pauseActionIntent = Intent(context, TimerReceiver::class.java)
                pauseActionIntent.action=ACTION_PAUSE_SERVICE
                pauseActionIntent.putExtra(EXTRA_ALARM_IN, stopTime)
                val pendingIntentForPauseEvent = PendingIntent.getBroadcast(
                    context, ACTION_PAUSE_SERVICE.hashCode(), pauseActionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                return NotificationCompat.Action(0,"Pause",pendingIntentForPauseEvent)
            }
            ACTION_STOP_APP -> {
                // build stop service Intent
                val stopActionIntent = Intent(context, TimerReceiver::class.java)
                stopActionIntent.action=ACTION_STOP_APP
                val pendingIntentForStopEvent = PendingIntent.getBroadcast(
                    context, ACTION_STOP_APP.hashCode(), stopActionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                return NotificationCompat.Action(0,"Stop",pendingIntentForStopEvent)
            }
            else -> {
                return NotificationCompat.Action(0, "Dummy Event", null)
            }
        }
    }
    private fun formatTime(millis: Long): String {
        val sec = (millis / 1000) % 60
        val min = (millis / 60000) % 60
        return String.format("%02d:%02d", min, sec)
    }
}

/*
class TimerService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TRACKER","SERVICE STARTED")
        when (intent?.action) {
            ACTION_START_APP -> showStartNotification()
            ACTION_RESUME_SERVICE -> {
                val duration = intent.getLongExtra(EXTRA_DURATION, timeLeftMillis.takeIf { it > 0 } ?: 30_000L)
                startTimer(duration)
            }
            ACTION_PAUSE_SERVICE -> pauseTimer()
            ACTION_START_ALARM -> triggerAlarmNotification()
            ACTION_STOP_APP -> stopServiceInternal()
        }
        return START_STICKY
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun startTimer(duration: Long) {
        handleDismissingNotification(HIGH_NOTIFICATION_PAUSE_ID)
        handleDismissingNotification(URGENT_NOTIFICATION_START_ALARM_ID)
        updateCountdownNotification(duration)
        timeLeftMillis = duration
        endTimeMillis = SystemClock.elapsedRealtime() + duration
        // triggers alarm manager at exact time and start receiver
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Use exact alarm to ensure it hits precisely at 0 seconds
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            endTimeMillis,
            pendingIntent
        )
        stopSelf()
    }
    private fun handleDismissingNotification(id : Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(id)
    }

    private fun pauseTimer() {
        val manager = getSystemService(NotificationManager::class.java)
        handleDismissingNotification(HIGH_NOTIFICATION_RESUME_ID)
        val remaining = endTimeMillis - SystemClock.elapsedRealtime()
        timeLeftMillis = if (remaining > 0) remaining else 0
        cancelAlarm()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_TIMER)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("Timer Paused")
            .setContentText("Paused at ${formatTime(timeLeftMillis)}")
            .setOngoing(true)
            .setSilent(true) // Medium/High priority but silent during pause
            .addAction(createAction(ACTION_RESUME_SERVICE, "Resume", timeLeftMillis))
            .addAction(createAction(ACTION_STOP_APP, "Stop"))
            .build()
        manager.notify(HIGH_NOTIFICATION_PAUSE_ID,builder)
        stopSelf()
    }
    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TimerReceiver::class.java)
        // The PendingIntent must match the one used in startTimer exactly
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0, // Match the request code used earlier
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // This stops the TimerReceiver from ever firing
        alarmManager.cancel(pendingIntent)
    }

    @SuppressLint("FullScreenIntentPolicy")
    private fun triggerAlarmNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        handleDismissingNotification(HIGH_NOTIFICATION_RESUME_ID)
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
        manager.notify(URGENT_NOTIFICATION_START_ALARM_ID,builder.build())
        stopSelf()
    }

    private fun showStartNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_DROP_DOWN)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ready to start?")
            .setContentText("Tap start to begin your 30s timer")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(createAction(ACTION_RESUME_SERVICE, "Start", 30_000L))
            .build()
        manager.notify(URGENT_NOTIFICATION_START_ALARM_ID,builder)
        stopSelf()
    }
    private fun updateCountdownNotification(millisUntilFinished: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        // Calculate the absolute system time when the timer will hit zero
        val stopTime = SystemClock.elapsedRealtime() + millisUntilFinished
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_TIMER)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Timer Running")
            // The summary text can still be static or descriptive
            .setContentText("Countdown in progress")
            // --- CHRONOMETER SETTINGS ---
            .setWhen(stopTime)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            // ----------------------------
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)
            .addAction(createAction(ACTION_PAUSE_SERVICE, "Pause"))
            .addAction(createAction(ACTION_STOP_APP, "Stop"))
            .build()
        manager.notify(HIGH_NOTIFICATION_PAUSE_ID,builder)
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

    private fun stopServiceInternal() {
        cancelAlarm()
        handleDismissingNotification(URGENT_NOTIFICATION_START_ALARM_ID)
        handleDismissingNotification(HIGH_NOTIFICATION_PAUSE_ID)
        handleDismissingNotification(HIGH_NOTIFICATION_RESUME_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cancelAlarm()
        Log.d("TRACKER","SERVICE ENDED")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}*/