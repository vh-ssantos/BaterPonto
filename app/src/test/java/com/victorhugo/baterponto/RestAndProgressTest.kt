package com.victorhugo.baterponto

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RestAndProgressTest {
    private val date = LocalDate.of(2026, 9, 29)
    private fun at(h: Int, m: Int = 0) = date.atTime(h, m)
    private fun finished() = WorkDay().record(at(8)).record(at(21, 54), finalExit = true)

    @Test fun restStartsAtActualFinalExitWithElevenHourDefault() {
        val day = finished()
        assertEquals(date.plusDays(1).atTime(8, 54), day.entryAllowedAt())
        assertEquals(day.entryAllowedAt(), alertEvents(day, at(22)).single().at)
        assertNull(day.copy(finished = false).entryAllowedAt())
        assertTrue(alertEvents(day.copy(finished = false), at(22)).isEmpty())
    }

    @Test fun restSettingReschedulesAndToggleCancels() {
        val day = finished().copy(restMinutes = 720)
        assertEquals(date.plusDays(1).atTime(9, 54), day.entryAllowedAt())
        assertTrue(alertEvents(day.copy(restAlertsEnabled = false), at(22)).isEmpty())
        assertNotEquals(alertEvents(finished(), at(22)).single().key, alertEvents(day, at(22)).single().key)
    }

    @Test fun newDayKeepsRestUntilAnotherEntryIsRegistered() {
        val day = finished().copy(progressMode = ProgressMode.WHOLE_DAY).nextDay()
        assertEquals(finished().entryAllowedAt(), day.entryAllowedAt())
        assertEquals(ProgressMode.WHOLE_DAY, day.progressMode)
        assertEquals(listOf(3), alertEvents(day, date.plusDays(1).atTime(8, 0)).map { it.id })
        assertNull(day.record(date.plusDays(1).atTime(9, 0)).entryAllowedAt())
    }

    @Test fun oldRestDoesNotNotifyDaysLaterAndLimitsAreValidated() {
        assertTrue(alertEvents(finished(), at(22).plusDays(2)).isEmpty())
        assertNotNull(finished().copy(restMinutes = 0).validationError())
        assertNotNull(finished().copy(restMinutes = 1440).validationError())
    }

    @Test fun lastFifteenStartsAtNetNineHoursFortyFive() {
        val day = WorkDay(alertsEnabled = true, standardBreak = true).record(at(8))
        assertEquals(at(19, 15), progressStartsAt(day))
        assertFalse(progressVisible(day, at(19, 14)))
        assertTrue(progressVisible(day, at(19, 15)))
        assertTrue(progressVisible(day, at(19, 45)))
    }

    @Test fun progressPausesAndResumesWithoutCountingTimeOutside() {
        val day = WorkDay(alertsEnabled = true, progressMode = ProgressMode.WHOLE_DAY)
            .record(at(8)).record(at(12))
        assertTrue(progressVisible(day, at(14)))
        assertEquals(240L, day.workedMinutes(at(14)))
        assertFalse(progressVisible(day.copy(progressMode = ProgressMode.LAST_FIFTEEN), at(14)))
        assertNull(progressStartsAt(day.copy(progressMode = ProgressMode.LAST_FIFTEEN)))
        assertEquals(at(19, 45), progressStartsAt(day.record(at(14)).copy(progressMode = ProgressMode.LAST_FIFTEEN)))
    }

    @Test fun progressStopsForFinishedDisabledEmptyAndStaleDays() {
        val day = WorkDay(alertsEnabled = true, progressMode = ProgressMode.WHOLE_DAY).record(at(8))
        assertFalse(progressVisible(day.record(at(17), finalExit = true), at(18)))
        assertFalse(progressVisible(day.copy(alertsEnabled = false), at(18)))
        assertFalse(progressVisible(day.copy(progressMode = ProgressMode.OFF), at(18)))
        assertFalse(progressVisible(day.nextDay(), at(18)))
        assertFalse(progressVisible(day, at(8).plusDays(1)))
    }

    @Test fun colorBoundariesMatchLegend() {
        assertEquals(JourneyBand.GREEN, journeyBand(479))
        assertEquals(JourneyBand.YELLOW, journeyBand(480))
        assertEquals(JourneyBand.ORANGE, journeyBand(540))
        assertEquals(JourneyBand.RED, journeyBand(585))
        assertEquals(JourneyBand.RED, journeyBand(601))
    }
}
