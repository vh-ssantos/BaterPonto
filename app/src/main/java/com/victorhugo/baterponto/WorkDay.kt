package com.victorhugo.baterponto

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Alternating entry/exit records; breaks never count as work. */
data class WorkDay(
    val punches: List<LocalDateTime> = emptyList(),
    val finished: Boolean = false,
    val goalMinutes: Int = 480,
    val alertMinutes: Int = 10,
    val alertsEnabled: Boolean = false,
    val standardBreak: Boolean? = null,
    val lunchExits: Set<Int> = emptySet(),
    val restMinutes: Int = 660,
    val restAlertsEnabled: Boolean = true,
    val progressMode: ProgressMode = ProgressMode.LAST_FIFTEEN,
    val previousFinalExit: LocalDateTime? = null
) {
    val working: Boolean get() = punches.size % 2 == 1 && !finished
    val paused: Boolean get() = punches.isNotEmpty() && !working && !finished

    fun validationError(): String? = when {
        lunchExits.any { it !in punches.indices || it % 2 != 1 } -> "Registro de almoço inválido."
        restMinutes !in 1..1439 -> "O descanso deve estar entre 00:01 e 23:59."
        goalMinutes !in 1..600 -> "A meta deve estar entre 00:01 e 10:00."
        alertMinutes !in listOf(10, 15, 30) -> "Escolha 10, 15 ou 30 minutos de antecedência."
        finished && punches.size % 2 != 0 -> "Registre a saída antes de encerrar."
        punches.zipWithNext().any { (a, b) -> !b.isAfter(a) } ->
            "Cada horário precisa ser posterior ao registro anterior."
        punches.isNotEmpty() && Duration.between(punches.first(), punches.last()).toHours() >= 24 ->
            "Os registros de uma jornada devem caber em menos de 24 horas."
        else -> null
    }

    private fun workPeriods(): List<Pair<LocalDateTime, LocalDateTime?>> {
        val date = punches.firstOrNull()?.toLocalDate() ?: return emptyList()
        val lunchStart = date.atTime(11, 30)
        val lunchEnd = date.atTime(13, 0)
        return punches.chunked(2).flatMap { pair ->
            val start = pair.first()
            val end = pair.getOrNull(1)
            if (standardBreak != true || !start.isBefore(lunchEnd) ||
                (end != null && !end.isAfter(lunchStart))) {
                listOf(start to end)
            } else buildList {
                if (start.isBefore(lunchStart)) add(start to lunchStart)
                if (end == null || end.isAfter(lunchEnd)) add(lunchEnd to end)
            }
        }
    }

    fun inStandardBreak(now: LocalDateTime): Boolean {
        val date = punches.firstOrNull()?.toLocalDate() ?: return false
        return standardBreak == true && working &&
            !now.isBefore(date.atTime(11, 30)) && now.isBefore(date.atTime(13, 0))
    }

    fun workedMinutes(now: LocalDateTime): Long = workPeriods().sumOf { (start, end) ->
        Duration.between(start, end?.let { minOf(it, now) } ?: now).toMinutes().coerceAtLeast(0)
    }

    fun breakMinutes(now: LocalDateTime): Long {
        val start = punches.firstOrNull() ?: return 0
        val end = if (finished) minOf(punches.last(), now) else now
        return (Duration.between(start, end).toMinutes().coerceAtLeast(0) - workedMinutes(now)).coerceAtLeast(0)
    }

    /** A reached milestone stays at its actual time, even after further breaks. */
    fun milestone(minutes: Int): LocalDateTime? {
        var remaining = minutes.toLong()
        for ((start, end) in workPeriods()) {
            if (end == null) return if (finished) null else start.plusMinutes(remaining)
            val worked = Duration.between(start, end).toMinutes()
            if (worked >= remaining) return start.plusMinutes(remaining)
            remaining -= worked
        }
        return null // On a break: wait for the actual return time.
    }

    val atLunch: Boolean get() = paused && punches.lastIndex in lunchExits

    fun record(time: LocalDateTime, lunch: Boolean = false, finalExit: Boolean = false): WorkDay =
        copy(
            punches = punches + time,
            finished = finalExit,
            lunchExits = if (lunch && working && !finalExit) lunchExits + punches.size else lunchExits,
            standardBreak = if (lunch && working && !finalExit) false else standardBreak
        )

    fun removeLast(): WorkDay = copy(
        punches = punches.dropLast(1), finished = false,
        lunchExits = lunchExits.filter { it < punches.lastIndex }.toSet()
    )

    fun label(index: Int): String = when {
        index == 0 -> "Entrada inicial"
        index == punches.lastIndex && finished -> "Saída definitiva"
        index in lunchExits -> "Saída para almoço"
        index - 1 in lunchExits -> "Retorno do almoço"
        index % 2 == 1 -> "Saída temporária"
        else -> "Retorno ao trabalho"
    }

    fun timeline(): List<DayPoint> {
        val rows = punches.mapIndexed { index, time -> DayPoint(time, label(index), index) }.toMutableList()
        if (standardBreak == true && punches.isNotEmpty()) {
            val date = punches.first().toLocalDate()
            val start = date.atTime(11, 30)
            val end = date.atTime(13, 0)
            // Only display the automatic interval that actually intersects a work period.
            for (pair in punches.chunked(2)) {
                val from = maxOf(pair.first(), start)
                val to = minOf(pair.getOrNull(1) ?: end, end)
                if (from.isBefore(to)) {
                    if (from !in punches) rows += DayPoint(from, "Saída para almoço", null)
                    if (to !in punches) rows += DayPoint(to, "Retorno do almoço", null)
                }
            }
        }
        return rows.sortedBy { it.time }
    }

    fun restBase(): LocalDateTime? =
        if (finished) punches.lastOrNull() else if (punches.isEmpty()) previousFinalExit else null

    fun entryAllowedAt(): LocalDateTime? = restBase()?.plusMinutes(restMinutes.toLong())

    fun nextDay() = WorkDay(
        goalMinutes = goalMinutes, alertMinutes = alertMinutes, alertsEnabled = alertsEnabled,
        restMinutes = restMinutes, restAlertsEnabled = restAlertsEnabled, progressMode = progressMode,
        previousFinalExit = if (finished) punches.lastOrNull() else previousFinalExit
    )
}

fun durationLabel(minutes: Long): String = "%02dh %02dmin".format(minutes / 60, minutes % 60)
fun clockLabel(time: LocalDateTime): String = time.format(DateTimeFormatter.ofPattern("HH:mm"))
fun datedClock(time: LocalDateTime, day: WorkDay): String =
    clockLabel(time) + if (day.punches.firstOrNull()?.toLocalDate()?.let { it != time.toLocalDate() } == true)
        time.format(DateTimeFormatter.ofPattern(" · dd/MM")) else ""
