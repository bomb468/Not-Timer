package com.example.notificationtimerapp

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.notificationtimerapp.MyApplication.Companion.CHANNEL_ID_ALARM
import com.example.notificationtimerapp.MyApplication.Companion.CHANNEL_ID_DROP_DOWN
import com.example.notificationtimerapp.MyApplication.Companion.CHANNEL_ID_TIMER

class TimerReceiver : BroadcastReceiver() {
    companion object{
        const val DURATION_OF_TIMER = 60_000L
        const val URGENT_NOTIFICATION_START_ALARM_ID = 1001
        const val HIGH_NOTIFICATION_ID = 1002

        const val ACTION_START_APP = "startApp"
        const val ACTION_STOP_APP = "stopApp"
        const val ACTION_RESUME_SERVICE = "resumeService"
        const val ACTION_START_ALARM = "startAlarm"
        const val ACTION_SHOW_DISMISSING_TOAST = "showToast"

        const val EXTRA_DURATION = "duration"
        const val EXTRA_ALARM_IN = "alarm_in"
    }
    @SuppressLint("ScheduleExactAlarm")
    override fun onReceive(context: Context, intent: Intent) {
        // Log.d("TRACKER","Service Received")
        when(intent.action) {
            ACTION_START_APP -> {
                // building notification
                val manager = context.getSystemService(NotificationManager::class.java)
                val builder = NotificationCompat.Builder(context, CHANNEL_ID_DROP_DOWN)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Ready to start?")
                    .setContentText("Tap start to begin your 10 minute timer")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .addAction(provideEventAction(context, ACTION_RESUME_SERVICE, DURATION_OF_TIMER))
                    .build()
                manager.notify(URGENT_NOTIFICATION_START_ALARM_ID,builder)
            }
            ACTION_STOP_APP -> {
                cancelAlarm(context)
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.cancel(HIGH_NOTIFICATION_ID)
                manager.cancel(URGENT_NOTIFICATION_START_ALARM_ID)
            }
            ACTION_RESUME_SERVICE -> {
                // get duration from extras
                val duration = intent.getLongExtra(EXTRA_DURATION, DURATION_OF_TIMER)
                val manager = context.getSystemService(NotificationManager::class.java)
                // cancel previous notifications if present
                manager.cancel(URGENT_NOTIFICATION_START_ALARM_ID)
                // Cancel any existing alarm before scheduling a new one to prevent "ghost" triggers
                cancelAlarm(context)
                // Calculate the absolute system time when the timer will hit zero
                val stopTime = SystemClock.elapsedRealtime() + duration
                val wallClockStopTime = System.currentTimeMillis() + duration
                // set up dismissing action
                val recoverIntent = Intent(context, TimerReceiver::class.java).apply {
                    action = ACTION_SHOW_DISMISSING_TOAST
                    putExtra(EXTRA_ALARM_IN, wallClockStopTime)
                }
                val recoverPendingIntent = PendingIntent.getBroadcast(
                    context,
                    ACTION_SHOW_DISMISSING_TOAST.hashCode(), // Unique request code
                    recoverIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                // build notification
                val builder = NotificationCompat.Builder(context, CHANNEL_ID_TIMER)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("Timer Running")
                    .setContentText("Countdown in progress")
                    .setWhen(wallClockStopTime)
                    .setDeleteIntent(recoverPendingIntent) // Fires if they manage to clear it
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setOngoing(true)
                    .setShowWhen(true)
                    .setSilent(true)
                    .addAction(provideEventAction(context, ACTION_STOP_APP))
                    .build()
                manager.notify(HIGH_NOTIFICATION_ID, builder)
                val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
                // set alarm to hit at exactly zero.
                val alarmActionIntent = Intent(context, TimerReceiver::class.java).apply {
                    action = ACTION_START_ALARM
                }
                val pendingIntentForAlarmEvent = PendingIntent.getBroadcast(
                    context,
                    ACTION_START_ALARM.hashCode(),
                    alarmActionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                // Use exact alarm to ensure it hits precisely at 0 seconds
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    wallClockStopTime,
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
                    .addAction(provideEventAction(context,ACTION_RESUME_SERVICE, DURATION_OF_TIMER, "Repeat"))
                    .addAction(provideEventAction(context,ACTION_STOP_APP))
                manager.notify(URGENT_NOTIFICATION_START_ALARM_ID,builder.build())
            }
            ACTION_SHOW_DISMISSING_TOAST -> {
                val alarmIn = intent.getLongExtra(EXTRA_ALARM_IN, DURATION_OF_TIMER) - SystemClock.elapsedRealtime()
                Toast.makeText(context, "Alarm will ring in the next ${alarmIn/60000} minutes and ${alarmIn%60000/1000} seconds", Toast.LENGTH_LONG).show()
            }
        }
        // Log.d("TRACKER","Service Ended")
    }
    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
        val intentOfAlarm = Intent(context, TimerReceiver::class.java)
        intentOfAlarm.action=ACTION_START_ALARM
        // The PendingIntent must match the one used in startTimer exactly
        val pendingIntentOfAlarm = PendingIntent.getBroadcast(
            context,
            ACTION_START_ALARM.hashCode(), // Match the request code used earlier
            intentOfAlarm,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntentOfAlarm != null) {
            // 1. Tell AlarmManager to stop tracking it
            alarmManager.cancel(pendingIntentOfAlarm)
            // 2. IMPORTANT: Invalidate the token itself.
            // Any broadcast already "in flight" with this token will now fail.
            pendingIntentOfAlarm.cancel()
        }
        val intentToast = Intent(context, TimerReceiver::class.java).apply { action = ACTION_SHOW_DISMISSING_TOAST }
        val pendingToast = PendingIntent.getBroadcast(
            context,
            ACTION_SHOW_DISMISSING_TOAST.hashCode(),
            intentToast,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        pendingToast?.cancel()
    }
    private fun provideEventAction(context : Context, action : String, stopTime : Long? = null, label : String? = null) : NotificationCompat.Action {
        when(action){
            ACTION_RESUME_SERVICE -> {
                // build resume service Intent
                val resumeActionIntent = Intent(context, TimerReceiver::class.java)
                resumeActionIntent.action = ACTION_RESUME_SERVICE
                resumeActionIntent.putExtra(EXTRA_DURATION, stopTime ?: DURATION_OF_TIMER)
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