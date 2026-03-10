package com.example.notificationtimerapp

import android.R
import android.R.attr.action
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.notificationtimerapp.MyApplication.Companion.CHANNEL_ID_ALARM
import com.example.notificationtimerapp.MyApplication.Companion.CHANNEL_ID_DROP_DOWN
import com.example.notificationtimerapp.MyApplication.Companion.CHANNEL_ID_TIMER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimerService : Service() {
    companion object {
        const val TIMER_DURATION = 60_000L
        // the only notification ID
        const val NOTIFICATION_ID = 1001
        // list of actions in the app
        const val ACTION_START_APP = "startApp"
        const val ACTION_STOP_APP = "stopApp"
        const val ACTION_RESUME_SERVICE = "resumeService"
        const val ACTION_PAUSE_SERVICE = "pauseService"

        // provides event actions for the notifications
        private fun provideEventAction(
            context: Context,
            action: String,
            label: String? = null,
            stopTime: Long? = null
        ): NotificationCompat.Action {
            when (action) {
                ACTION_RESUME_SERVICE -> {
                    // build resume service Intent
                    val resumeActionIntent = Intent(context, TimerService::class.java)
                    resumeActionIntent.action = ACTION_RESUME_SERVICE
                    val pendingIntentForResumeEvent = PendingIntent.getService(
                        context, ACTION_RESUME_SERVICE.hashCode(), resumeActionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    return NotificationCompat.Action(0, label, pendingIntentForResumeEvent)
                }

                ACTION_PAUSE_SERVICE -> {
                    // build resume service Intent
                    val pauseActionIntent = Intent(context, TimerService::class.java)
                    pauseActionIntent.action = ACTION_PAUSE_SERVICE
                    pauseActionIntent.putExtra("stopTime", stopTime)
                    val pendingIntentForPauseEvent = PendingIntent.getService(
                        context, ACTION_PAUSE_SERVICE.hashCode(), pauseActionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    return NotificationCompat.Action(0, label, pendingIntentForPauseEvent)
                }
                ACTION_STOP_APP -> {
                    // build stop service Intent
                    val stopActionIntent = Intent(context, TimerService::class.java)
                    stopActionIntent.action = ACTION_STOP_APP
                    val pendingIntentForStopEvent = PendingIntent.getService(
                        context, ACTION_STOP_APP.hashCode(), stopActionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    return NotificationCompat.Action(0, "Stop", pendingIntentForStopEvent)
                }
                else -> {
                    return NotificationCompat.Action(0, "Dummy Event", null)
                }
            }
        }
    }
    // the only place to overwrite to edit duration in the app
    var duration : Long = TIMER_DURATION
    // reference to job and scope for coroutines
    var job : Job? = null
    val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // hold manager reference
        val manager = getSystemService(NotificationManager::class.java)
        // pending intent for when user swipes any notification or system kills it
        val deleteNotificationIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_STOP_APP
        }
        val pendingIntentForDeleteEvent: PendingIntent? = PendingIntent.getService(this,
            ACTION_STOP_APP.hashCode(),
            deleteNotificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // when for all actions
        when(intent?.action){
            ACTION_START_APP -> {
                // starting foreground service
                val builder = NotificationCompat.Builder(this, CHANNEL_ID_DROP_DOWN)
                    .setSmallIcon(R.drawable.ic_dialog_info)
                    .setContentTitle("Ready to start?")
                    .setContentText("Tap start to begin your timer")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .addAction(provideEventAction(this, ACTION_RESUME_SERVICE, "Start"))
                    .setDeleteIntent(pendingIntentForDeleteEvent)
                    .setOngoing(true)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
                    startForeground(NOTIFICATION_ID,builder, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                }else{
                    startForeground(NOTIFICATION_ID,builder)
                }
            }
            ACTION_RESUME_SERVICE -> {
                job?.cancel() // Guard against overlapping coroutines
                val stopIn = duration + SystemClock.elapsedRealtime()
                // build notification
                val builder = NotificationCompat.Builder(this, CHANNEL_ID_TIMER)
                    .setSmallIcon(R.drawable.ic_media_play)
                    .setContentTitle("Timer Running")
                    .setContentText("Countdown in progress")
                    .setWhen(stopIn)
                    .addAction(provideEventAction(this@TimerService, ACTION_PAUSE_SERVICE, "Pause",stopIn))
                    .addAction(provideEventAction(this@TimerService, ACTION_STOP_APP))
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setOngoing(true)
                    .setShowWhen(true)
                    .setSilent(true)
                    .setDeleteIntent(pendingIntentForDeleteEvent)
                    .build()
                manager.notify(NOTIFICATION_ID,builder)
                // handle alarm notification
                job = serviceScope.launch {
                    delay(duration)
                    // show alarm notification
                    val builder = NotificationCompat.Builder(this@TimerService, CHANNEL_ID_ALARM)
                        .setSmallIcon(R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("Time is Up!")
                        .setContentText("Timer reached 00:00")
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setOngoing(true)
                        //.setFullScreenIntent(null, true) // Increases urgency on many devices
                        .addAction(provideEventAction(this@TimerService,ACTION_RESUME_SERVICE, "Repeat"))
                        .addAction(provideEventAction(this@TimerService,ACTION_STOP_APP))
                        .setDeleteIntent(pendingIntentForDeleteEvent)
                        .build()
                    manager.notify(NOTIFICATION_ID,builder)
                    duration = TIMER_DURATION
                }
            }
            ACTION_PAUSE_SERVICE -> {
                // cancel existing job
                job?.cancel()
                // update duration
                val stopTime = intent.getLongExtra("stopTime",0L)
                if (stopTime == 0L){
                    // stop time will never be 0
                    return START_STICKY
                }
                duration = stopTime - SystemClock.elapsedRealtime()
                // update notification
                val builder = NotificationCompat.Builder(this, CHANNEL_ID_TIMER)
                    .setSmallIcon(R.drawable.ic_media_pause)
                    .setContentTitle("Timer Paused")
                    .setContentText("Paused at ${formatDuration(duration)}")
                    .setWhen(SystemClock.elapsedRealtime() + duration)
                    .setUsesChronometer(false)
                    .setShowWhen(true)
                    .addAction(provideEventAction(this, ACTION_RESUME_SERVICE, "Resume"))
                    .addAction(provideEventAction(this, ACTION_STOP_APP))
                    .setSilent(true)
                    .setDeleteIntent(pendingIntentForDeleteEvent)
                    .build()
                manager.notify(NOTIFICATION_ID,builder)
            }
            ACTION_STOP_APP -> {
                // cancel job
                job?.cancel()
                // reset duration if the app is not dead before user starts it again
                duration = TIMER_DURATION
                // cancel notification immediately
                manager.cancel(NOTIFICATION_ID)
                // let system stop the service
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }
    override fun onCreate() {
        super.onCreate()
        //Log.d("TRACKER","Service Started")
    }
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        //Log.d("TRACKER","Service Ended")
    }
    override fun onBind(p0: Intent?): IBinder? = null
    private fun formatDuration(ms: Long): String {
        val totalSeconds = 0.coerceAtLeast((ms / 1000).toInt()) // Avoid negative numbers
        val minutes = totalSeconds /60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.getDefault(), "%dm %02ds", minutes, seconds)
    }
}