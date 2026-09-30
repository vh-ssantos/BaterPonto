package com.victorhugo.baterponto

import java.time.LocalDateTime

enum class ProgressMode(val label: String) {
    OFF("Desativada"), LAST_FIFTEEN("Últimos 15 min"), WHOLE_DAY("Jornada inteira")
}

enum class JourneyBand(val label: String, val argb: Int) {
    GREEN("Dentro da meta", 0xFF25824F.toInt()),
    YELLOW("Acima de 8h", 0xFFB58A00.toInt()),
    ORANGE("Perto do limite", 0xFFE37416.toInt()),
    RED("Atenção ao limite", 0xFFC92D39.toInt())
}

fun journeyBand(minutes: Long): JourneyBand = when {
    minutes >= 585 -> JourneyBand.RED
    minutes >= 540 -> JourneyBand.ORANGE
    minutes >= 480 -> JourneyBand.YELLOW
    else -> JourneyBand.GREEN
}

fun progressVisible(day: WorkDay, now: LocalDateTime): Boolean =
    day.alertsEnabled && day.punches.isNotEmpty() && !day.finished &&
        !now.isBefore(day.punches.first()) &&
        now.isBefore(day.punches.first().plusHours(24)) &&
        when (day.progressMode) {
            ProgressMode.OFF -> false
            ProgressMode.WHOLE_DAY -> true
            ProgressMode.LAST_FIFTEEN -> day.workedMinutes(now) >= 585
        }

fun progressStartsAt(day: WorkDay): LocalDateTime? {
    if (!day.alertsEnabled || day.finished || day.progressMode == ProgressMode.OFF) return null
    return if (day.progressMode == ProgressMode.WHOLE_DAY) day.punches.firstOrNull()
    else if (day.working) day.milestone(585) else null
}
