package com.victorhugo.baterponto

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.victorhugo.baterponto.ui.theme.BaterPontoTheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WorkAlerts.createChannel(this)
        setContent { BaterPontoTheme { WorkDayApp() } }
    }
}

@Composable
private fun WorkDayApp() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val store = remember { WorkDayStore(context.applicationContext) }
    var day by remember { mutableStateOf(store.load()) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var notifications by remember { mutableStateOf(WorkAlerts.notificationsAllowed(context)) }
    var exact by remember { mutableStateOf(WorkAlerts.exactAllowed(context)) }
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notifications = WorkAlerts.notificationsAllowed(context)
        WorkAlerts.reschedule(context, allowServiceStart = true)
    }
    val update: (WorkDay) -> Unit = { updated ->
        val requestPermission = (!day.alertsEnabled && updated.alertsEnabled) ||
            (!day.restAlertsEnabled && updated.restAlertsEnabled) ||
            (!day.finished && updated.finished && updated.restAlertsEnabled)
        store.save(updated)
        day = updated
        WorkAlerts.reschedule(context, allowServiceStart = true)
        if (requestPermission && !notifications && Build.VERSION.SDK_INT >= 33)
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                now = LocalDateTime.now()
                notifications = WorkAlerts.notificationsAllowed(context)
                exact = WorkAlerts.exactAllowed(context)
                WorkAlerts.reschedule(context, allowServiceStart = true)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = LocalDateTime.now()
                delay(1000.milliseconds)
            }
        }
    }
    WorkDayScreen(
        day = day, now = now, notificationsAllowed = notifications, exactAllowed = exact,
        onChange = update,
        onEnableAlerts = { enabled ->
            update(day.copy(alertsEnabled = enabled))

        },
        onNotificationSettings = {
            context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, WorkAlerts.CHANNEL))
        },
        onExactSettings = {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                "package:${context.packageName}".toUri()))
        }
    )
}
