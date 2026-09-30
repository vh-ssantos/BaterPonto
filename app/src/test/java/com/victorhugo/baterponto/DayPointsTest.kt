package com.victorhugo.baterponto

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DayPointsTest {
    private val date = LocalDate.of(2026, 9, 29)
    private fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute)

    @Test fun manualLunchAndOvertimeUseDistinctPairs() {
        val day = WorkDay().record(at(8, 21))
            .record(at(11, 30), lunch = true).record(at(13))
            .record(at(17, 47)).record(at(19, 50))
            .record(at(21, 54), finalExit = true)
        assertNull(day.validationError())
        assertEquals(600L, day.workedMinutes(at(23)))
        assertEquals(listOf("Entrada inicial", "Saída para almoço", "Retorno do almoço",
            "Saída temporária", "Retorno ao trabalho", "Saída definitiva"),
            day.timeline().map { it.label })
        assertTrue(day.timeline().all { it.punchIndex != null })
    }

    @Test fun automaticLunchIsVisibleWithOvertimeAndDoesNotRequireManualReturn() {
        val day = WorkDay(standardBreak = true).record(at(8, 21))
            .record(at(17, 47)).record(at(19, 50))
        assertEquals(at(21, 54), day.milestone(600))
        assertEquals(listOf(at(8, 21), at(11, 30), at(13), at(17, 47), at(19, 50)),
            day.timeline().map { it.time })
        assertEquals(2, day.timeline().count { it.punchIndex == null })
        assertTrue(day.working)
        assertEquals(listOf(0, 1, 2), day.timeline().mapNotNull { it.punchIndex })
    }

    @Test fun returningFromLunchAndTemporaryExitHaveDifferentStates() {
        val lunch = WorkDay().record(at(8)).record(at(11, 30), lunch = true)
        assertTrue(lunch.atLunch)
        val returned = lunch.record(at(13))
        assertFalse(returned.atLunch)
        val outside = returned.record(at(17))
        assertTrue(outside.paused)
        assertFalse(outside.atLunch)
        assertNull(outside.milestone(600))
        assertEquals(at(21, 30), outside.record(at(19)).milestone(600))
    }

    @Test fun savingManualLunchReplacesAutomaticAndCancelDoesNotMutateDay() {
        val original = WorkDay(standardBreak = true).record(at(8))
        val manual = original.record(at(12), lunch = true).record(at(13))
        assertEquals(true, original.standardBreak)
        assertEquals(false, manual.standardBreak)
        assertEquals(at(17), manual.milestone(480))
        assertTrue(manual.timeline().all { it.punchIndex != null })
    }

    @Test fun removingAndReopeningPreservesPairLabels() {
        val day = WorkDay().record(at(8)).record(at(12), lunch = true).record(at(13))
        assertTrue(day.removeLast().atLunch)
        assertTrue(day.removeLast().removeLast().lunchExits.isEmpty())
        val finished = day.record(at(17), finalExit = true)
        assertEquals("Saída definitiva", finished.label(3))
        assertEquals("Saída temporária", finished.copy(finished = false).label(3))
        assertTrue(finished.nextDay().lunchExits.isEmpty())
    }

    @Test fun automaticRowsStayInsideWorkedDayAndDoNotDuplicateManualTimes() {
        val early = WorkDay(standardBreak = true).record(at(8)).record(at(11), finalExit = true)
        assertEquals(2, early.timeline().size)
        val exact = WorkDay(standardBreak = true).record(at(8)).record(at(11, 30)).record(at(13))
        assertEquals(3, exact.timeline().size)
        assertEquals("Saída temporária", exact.label(1)) // Legacy untyped records stay unchanged.
    }
}
