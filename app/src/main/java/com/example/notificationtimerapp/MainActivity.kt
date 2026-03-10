package com.example.notificationtimerapp

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.notificationtimerapp.ui.theme.NotificationTimerAppTheme

class MyApplication : Application() {
    companion object{
        const val CHANNEL_ID_ALARM = "alarm_channel" // MAX Priority
        const val CHANNEL_ID_TIMER = "timer_channel" // HIGH Priority
        const val CHANNEL_ID_DROP_DOWN = "drop_down_channel" // MAX Priority
    }
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        // 1. Alarm Channel: MAX Priority + Sound
        val alarmChannel = NotificationChannel(
            CHANNEL_ID_ALARM, "Alarm Notification", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Used for start alarms on timer completion"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setBypassDnd(true)
            // Set the default alarm sound at the channel level
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            setSound(alarmSound, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
        }
        val dropDownChannel = NotificationChannel(
            CHANNEL_ID_DROP_DOWN, "Start Timer Notification", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null) // Ensures no sound every second
            enableVibration(false)
            description = "Used for the start timer notification"
        }
        val timerChannel = NotificationChannel(
            CHANNEL_ID_TIMER, "Ongoing Timer Notification", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null) // Ensures no sound every second
            enableVibration(false)
        }
        manager.createNotificationChannel(alarmChannel)
        manager.createNotificationChannel(timerChannel)
        manager.createNotificationChannel(dropDownChannel)
    }
}
class MainActivity : ComponentActivity() {
    var isNotificationPermissionGranted by mutableStateOf(false)
    var isExactAlarmPermissionGranted by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotificationTimerAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        hasNotificationPermission = isNotificationPermissionGranted,
                        hasExactAlarmPermission = isExactAlarmPermissionGranted
                    )
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        isNotificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires explicit POST_NOTIFICATIONS check
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Before Android 13, check if notifications are enabled via NotificationManagerCompat
            androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
        isExactAlarmPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true
        if (isNotificationPermissionGranted && isExactAlarmPermissionGranted) {
            // start broadcast receiver
            sendBroadcast(Intent(
                this,
                TimerReceiver::class.java
            ).apply {
                action = TimerReceiver.ACTION_START_APP
            })
            finish()
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, hasNotificationPermission : Boolean, hasExactAlarmPermission : Boolean) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {
            // Do nothing to prevent dismissal
        },
        confirmButton = {
            Button(onClick = {
                var intent = Intent()
                if (!hasNotificationPermission){
                    intent = intent.apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
                if (!hasExactAlarmPermission){
                    intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
                context.startActivity(intent)
            }) {
                if (!hasNotificationPermission)
                    Text("Open Notification Settings")
                else if (!hasExactAlarmPermission){
                    Text("Open Exact Alarm Settings")
                }
            }
        },
        title = { Text("Permissions Required") },
        text = { Text("Notification and Schedule Exact Alarm Permissions are required for this app to function. Please enable them in settings.") },
        // This prevents the user from clicking outside or pressing back to close it
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NotificationTimerAppTheme {
        MainScreen(hasNotificationPermission = true, hasExactAlarmPermission = false)
    }
}