package com.victorhugo.baterponto

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.time.LocalDateTime
import java.time.ZoneId

data class AlertEvent(val id: Int, val at: LocalDateTime, val deadline: LocalDateTime) {
    val key: String get() = "$id:$at:$deadline"
}

/** Pure scheduling rules shared by the receiver and the UI. */
fun alertEvents(day: WorkDay, now: LocalDateTime): List<AlertEvent> = buildList {
    if (day.restAlertsEnabled) {
        day.entryAllowedAt()?.let { ready ->
            if (!now.isAfter(ready.plusHours(12))) add(AlertEvent(3, ready, ready))
        }
    }
    if (day.alertsEnabled && day.working) {
        day.milestone(600)?.let { deadline ->
            if (!now.isAfter(deadline.plusMinutes(15))) {
                if (now.isBefore(deadline)) add(AlertEvent(1, deadline.minusMinutes(day.alertMinutes.toLong()), deadline))
                add(AlertEvent(2, deadline, deadline))
            }
        }
    }
}

object WorkAlerts {
    const val CHANNEL = "work_limit"
    private const val ACTION = "com.victorhugo.baterponto.WORK_ALERT"

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Jornada e descanso", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos antes de completar 10 horas de trabalho e ao atingir o limite."
                enableVibration(true)
            }
        )
    }

    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun exactAllowed(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pending(context: Context, id: Int, key: String = ""): PendingIntent =
        PendingIntent.getBroadcast(context, id,
            Intent(context, WorkAlertReceiver::class.java).setAction(ACTION).putExtra("key", key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun reschedule(context: Context, allowServiceStart: Boolean = false) {
        createChannel(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        (1..3).forEach { manager.cancel(pending(context, it)) }
        val store = WorkDayStore(context)
        val day = store.load()
        if (!day.working || !day.alertsEnabled) {
            val notifications = context.getSystemService(NotificationManager::class.java)
            (1..2).forEach(notifications::cancel)
        }
        if (!day.restAlertsEnabled || day.entryAllowedAt() == null)
            context.getSystemService(NotificationManager::class.java).cancel(3)
        JourneyNotification.sync(context, allowServiceStart)
        if (!notificationsAllowed(context)) return
        val now = LocalDateTime.now()
        for (event in alertEvents(day, now)) {
            if (store.delivered(event.key)) continue
            val trigger = maxOf(System.currentTimeMillis() + 1000,
                event.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            val intent = pending(context, event.id, event.key)
            try {
                if (manager.canScheduleExactAlarms()) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
                }
            } catch (_: SecurityException) {
                // Permission may be revoked between checking and scheduling.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
            }
        }
    }

    fun deliver(context: Context, key: String?) {
        val store = WorkDayStore(context)
        val now = LocalDateTime.now()
        val event = alertEvents(store.load(), now)
            .firstOrNull { it.key == key && !now.isBefore(it.at) } ?: return
        if (store.delivered(event.key) || !notificationsAllowed(context)) return
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = when (event.id) {
            1 -> "Seu limite está chegando"
            3 -> "Entrada liberada"
            else -> "Você completou 10 horas"
        }
        val message = if (event.id == 3)
            "Seu descanso de ${durationLabel(store.load().restMinutes.toLong())} terminou. Entrada a partir de ${clockLabel(event.at)}."
        else if (event.id == 1)
            "Seu limite de 10h é às ${clockLabel(event.deadline)}. Prepare-se para registrar a saída."
        else "O limite de 10h foi atingido às ${clockLabel(event.deadline)}. Confira seu ponto."
        try {
            context.getSystemService(NotificationManager::class.java).notify(event.id,
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification_clock)
                    .setContentTitle(title).setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(open).setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).build())
            store.markDelivered(event.key)
        } catch (_: SecurityException) {
            // Notifications were disabled while this broadcast was being handled.
        }
    }
}

class WorkAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("progress", false)) {
            JourneyNotification.sync(context, intent.getBooleanExtra("exact", false))
        } else {
            WorkAlerts.deliver(context, intent.getStringExtra("key"))
        }
    }
}

class RestoreAlertsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )) WorkAlerts.reschedule(context, allowServiceStart = true)
    }
}
