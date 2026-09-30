package com.victorhugo.baterponto

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import java.time.LocalDateTime
import java.time.ZoneId

object JourneyNotification {
    const val ID = 4
    const val CHANNEL = "journey_progress"

    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Progresso da jornada", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Contagem silenciosa do tempo trabalhado até o limite de 10 horas."
                setSound(null, null)
                enableVibration(false)
            })
    }

    fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun alarmIntent(context: Context, exact: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context, ID, Intent(context, WorkAlertReceiver::class.java)
            .setAction("com.victorhugo.baterponto.PROGRESS_START")
            .putExtra("progress", true).putExtra("exact", exact),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun sync(context: Context, allowStart: Boolean) {
        channel(context)
        val day = WorkDayStore(context).load()
        val now = LocalDateTime.now()
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(alarmIntent(context, false))
        if (!allowed(context) || !progressVisible(day, now)) {
            context.stopService(Intent(context, JourneyProgressService::class.java))
            context.getSystemService(NotificationManager::class.java).cancel(ID)
        }
        if (!allowed(context)) return
        if (progressVisible(day, now)) {
            if (allowStart) {
                try {
                    context.startForegroundService(Intent(context, JourneyProgressService::class.java))
                } catch (_: ForegroundServiceStartNotAllowedException) {
                    showSnapshot(context, day, now)
                } catch (_: SecurityException) {
                    showSnapshot(context, day, now)
                }
            } else if (!JourneyProgressService.running) showSnapshot(context, day, now)
            return
        }
        val start = progressStartsAt(day) ?: return
        if (!start.isAfter(now) || !start.isBefore(day.punches.first().plusHours(24))) return
        val trigger = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmIntent(context, true))
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmIntent(context, false))
            }
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmIntent(context, false))
        }
    }

    private fun showSnapshot(context: Context, day: WorkDay, now: LocalDateTime) {
        try {
            context.getSystemService(NotificationManager::class.java).notify(ID,
                build(context, day, now, live = false))
        } catch (_: SecurityException) { }
    }

    fun build(context: Context, day: WorkDay, now: LocalDateTime, live: Boolean = true): Notification {
        val worked = day.workedMinutes(now)
        val band = journeyBand(worked)
        val counting = live && day.working && !day.inStandardBreak(now)
        val status = when {
            !live -> "Abra o app para iniciar a contagem ao vivo"
            day.atLunch || day.inStandardBreak(now) -> "Almoço · contagem pausada"
            day.paused -> "Fora do trabalho · contagem pausada"
            worked >= 600 -> "Limite de 10 horas atingido"
            else -> "${band.label} · faltam ${durationLabel(600 - worked)}"
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = "Tempo de trabalho · %02d:%02d".format(worked / 60, worked % 60)
        val bitmap = Bitmap.createBitmap(600, 16, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFFCFD6D2.toInt()
        canvas.drawRoundRect(0f, 0f, 600f, 16f, 8f, 8f, paint)
        val progress = worked.coerceIn(0, 600).toFloat()
        if (day.progressMode == ProgressMode.WHOLE_DAY) {
            val ranges = listOf(0 to 480, 480 to 540, 540 to 585, 585 to 600)
            ranges.forEachIndexed { index, (from, to) ->
                if (progress > from) {
                    paint.color = JourneyBand.entries[index].argb
                    canvas.drawRect(from.toFloat(), 0f, minOf(progress, to.toFloat()), 16f, paint)
                }
            }
        } else {
            paint.color = band.argb
            canvas.drawRoundRect(0f, 0f, progress, 16f, 8f, 8f, paint)
        }
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (dark) Color.WHITE else Color.BLACK
        fun view(layout: Int) = RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.journey_title, title)
            setTextColor(R.id.journey_title, textColor)
            setImageViewBitmap(R.id.journey_bar, bitmap)
        }
        val compact = view(R.layout.notification_journey)
        val expanded = view(R.layout.notification_journey_expanded).apply {
            val seconds = worked * 60 + if (counting) now.second else 0
            setChronometer(R.id.journey_clock, SystemClock.elapsedRealtime() - seconds * 1000,
                if (counting) "Trabalhado: %s" else "Pausado: %s", counting)
            setTextColor(R.id.journey_clock, textColor)
            setTextViewText(R.id.journey_detail, status)
            setTextColor(R.id.journey_detail, textColor)
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_clock)
            .setContentTitle(title).setContentText(status)
            .setContentIntent(open).setOngoing(live).setOnlyAlertOnce(true).setSilent(true)
            .setColor(band.argb).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compact).setCustomBigContentView(expanded)
            .addAction(0, "Abrir jornada", open)
            .build()
    }
}
