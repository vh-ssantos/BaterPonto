package com.victorhugo.baterponto

import android.content.Context
import java.time.LocalDateTime

class WorkDayStore(context: Context) {
    private val prefs = context.getSharedPreferences("work_day", Context.MODE_PRIVATE)

    fun load(): WorkDay = runCatching {
        WorkDay(
            punches = prefs.getString("punches", "").orEmpty().split('|')
                .filter(String::isNotEmpty).map(LocalDateTime::parse),
            finished = prefs.getBoolean("finished", false),
            goalMinutes = prefs.getInt("goal", 480),
            alertMinutes = prefs.getInt("lead", 10),
            alertsEnabled = prefs.getBoolean("alerts", false),
            standardBreak = if (prefs.contains("standard_break")) prefs.getBoolean("standard_break", false) else null,
            lunchExits = prefs.getStringSet("lunch_exits", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
            restMinutes = prefs.getInt("rest_minutes", 660),
            restAlertsEnabled = prefs.getBoolean("rest_alerts", true),
            progressMode = ProgressMode.entries.firstOrNull { it.name == prefs.getString("progress_mode", "") }
                ?: ProgressMode.LAST_FIFTEEN,
            previousFinalExit = prefs.getString("previous_exit", null)?.let(LocalDateTime::parse)
        ).also { require(it.validationError() == null) }
    }.getOrElse { WorkDay() }

    fun save(day: WorkDay) {
        require(day.validationError() == null)
        prefs.edit()
            .putString("punches", day.punches.joinToString("|"))
            .putBoolean("finished", day.finished)
            .putStringSet("lunch_exits", day.lunchExits.map { it.toString() }.toSet())
            .putInt("goal", day.goalMinutes)
            .putInt("lead", day.alertMinutes)
            .putBoolean("alerts", day.alertsEnabled)
            .putInt("rest_minutes", day.restMinutes)
            .putBoolean("rest_alerts", day.restAlertsEnabled)
            .putString("progress_mode", day.progressMode.name)
            .putString("previous_exit", day.previousFinalExit?.toString())
            .apply {
                if (day.standardBreak == null) remove("standard_break")
                else putBoolean("standard_break", day.standardBreak)
            }
            .apply()
    }

    fun delivered(key: String): Boolean = prefs.getBoolean("sent_$key", false)
    fun markDelivered(key: String) { prefs.edit().putBoolean("sent_$key", true).apply() }
}
