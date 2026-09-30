package com.victorhugo.baterponto

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class WorkDayTest {
    private val date = LocalDate.of(2026, 9, 29)
    private fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute)
    private fun day(vararg times: LocalDateTime) = WorkDay(times.toList())

    @Test fun oneBreakMatchesSpreadsheet() {
        val day = day(at(8), at(11, 30), at(13))
        assertEquals(at(17, 30), day.milestone(480))
        assertEquals(at(19, 30), day.milestone(600))
        assertEquals(414L, day.workedMinutes(at(16, 24)))
        assertEquals(90L, day.breakMinutes(at(16, 24)))
    }

    @Test fun twoBreaksMatchSpreadsheetAndRest() {
        val day = day(at(8, 21), at(11, 30), at(13), at(17, 47), at(19, 50))
        assertEquals(at(19, 54), day.milestone(480))
        assertEquals(at(21, 54), day.milestone(600))
        assertEquals(date.plusDays(1).atTime(8, 54), day.milestone(600)!!.plusHours(11))
    }

    @Test fun openBreakFreezesWorkedTimeAndHidesUnreachedForecast() {
        val day = day(at(8), at(12))
        assertEquals(240L, day.workedMinutes(at(13)))
        assertEquals(60L, day.breakMinutes(at(13)))
        assertNull(day.milestone(480))
        assertTrue(day.paused)
        assertTrue(alertEvents(day.copy(alertsEnabled = true), at(13)).isEmpty())
    }

    @Test fun reachedMilestoneDoesNotMoveWithLaterBreaks() {
        val day = day(at(8), at(16, 30), at(17))
        assertEquals(at(16), day.milestone(480))
        assertEquals(at(18, 30), day.milestone(600))
    }

    @Test fun finishStopsTimeAndLimitAlertsButSchedulesRest() {
        val day = day(at(8), at(12), at(13), at(17)).copy(finished = true, alertsEnabled = true)
        assertEquals(480L, day.workedMinutes(at(23)))
        assertEquals(60L, day.breakMinutes(at(23)))
        assertEquals(at(17), day.milestone(480))
        assertNull(day.milestone(600))
        assertEquals(listOf(3), alertEvents(day, at(17)).map { it.id })
        assertEquals(date.plusDays(1).atTime(4, 0), alertEvents(day, at(17)).single().at)
    }

    @Test fun overnightKeepsDateAndMinutePrecision() {
        val day = day(at(22, 15), date.plusDays(1).atTime(1, 0), date.plusDays(1).atTime(2, 0))
        assertNull(day.validationError())
        assertEquals(date.plusDays(1).atTime(7, 15), day.milestone(480))
        assertEquals(date.plusDays(1).atTime(9, 15), day.milestone(600))
    }

    @Test fun customGoalAndEmptyState() {
        assertNull(WorkDay().milestone(480))
        assertEquals(0L, WorkDay().workedMinutes(at(10)))
        assertEquals(at(16, 48), day(at(8)).milestone(528))
    }

    @Test fun invalidOrderDuplicateAndDurationAreRejected() {
        assertNotNull(day(at(12), at(8)).validationError())
        assertNotNull(day(at(8), at(8)).validationError())
        assertNotNull(day(at(8), at(8).plusDays(1)).validationError())
        assertNotNull(WorkDay(goalMinutes = 601).validationError())
        assertNotNull(day(at(8)).copy(finished = true).validationError())
    }

    @Test fun alertsUseNetWorkAndUpdateAfterBreak() {
        val day = day(at(8), at(12), at(13)).copy(alertsEnabled = true, alertMinutes = 15)
        val events = alertEvents(day, at(14))
        assertEquals(listOf(at(18, 45), at(19)), events.map { it.at })
        assertEquals(listOf(1, 2), events.map { it.id })
        assertTrue(alertEvents(day.copy(alertsEnabled = false), at(14)).isEmpty())
    }

    @Test fun overdueWarningsAreSuppressedAndStaleDaysDoNotAlert() {
        val day = day(at(8)).copy(alertsEnabled = true)
        assertEquals(listOf(2), alertEvents(day, at(18)).map { it.id })
        assertTrue(alertEvents(day, at(18, 16)).isEmpty())
        assertTrue(alertEvents(day, at(18).plusDays(1)).isEmpty())
    }

    @Test fun earlyTickDoesNotCountFutureWork() {
        assertEquals(0L, day(at(8)).workedMinutes(at(7)))
        assertEquals(120L, day(at(8), at(12)).workedMinutes(at(10)))
    }

    @Test fun newDayKeepsOnlyPreferences() {
        val reset = day(at(8), at(17)).copy(finished = true, goalMinutes = 528, alertMinutes = 30, alertsEnabled = true).nextDay()
        assertTrue(reset.punches.isEmpty())
        assertFalse(reset.finished)
        assertEquals(528, reset.goalMinutes)
        assertEquals(30, reset.alertMinutes)
        assertTrue(reset.alertsEnabled)
    }
}
