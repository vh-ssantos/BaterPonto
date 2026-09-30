package com.victorhugo.baterponto

import androidx.compose.ui.text.AnnotatedString
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TimeAndLunchTest {
    private val date = LocalDate.of(2026, 9, 29)
    private fun at(h: Int, m: Int = 0) = date.atTime(h, m)
    private fun day(vararg hours: java.time.LocalDateTime) =
        WorkDay(punches = hours.toList(), standardBreak = true)

    @Test fun typingAndPastingClockDigits() {
        var text = ""
        for (digit in "2359") text = acceptTimeInput(text, text + digit)
        assertEquals("2359", text)
        assertEquals(1439, timeInputMinutes(text))
        assertEquals("0821", acceptTimeInput("", "08:21"))
        assertEquals("08:21", TimeMask.filter(AnnotatedString("0821")).text.text)
        assertEquals("08:", TimeMask.filter(AnnotatedString("08")).text.text)
    }

    @Test fun invalidHoursMinutesLettersAndFifthDigitAreRejected() {
        for (value in listOf("25", "30", "24", "1260", "127", "abcd", "12345"))
            assertEquals("", acceptTimeInput("", value))
        assertEquals("23", acceptTimeInput("23", "25"))
        assertNull(timeInputMinutes("123"))
        assertNull(timeInputMinutes("2400"))
        assertEquals(0, timeInputMinutes("0000"))
    }

    @Test fun maskCursorMappingsAreValidForEveryLength() {
        for (digits in listOf("", "0", "08", "082", "0821")) {
            val result = TimeMask.filter(AnnotatedString(digits))
            for (offset in 0..digits.length) {
                val transformed = result.offsetMapping.originalToTransformed(offset)
                assertTrue(transformed in 0..result.text.length)
                assertEquals(offset, result.offsetMapping.transformedToOriginal(transformed))
            }
            for (offset in 0..result.text.length)
                assertTrue(result.offsetMapping.transformedToOriginal(offset) in 0..digits.length)
        }
        assertEquals("082", acceptTimeInput("0821", "082"))
        assertEquals("", acceptTimeInput("0821", ""))
    }

    @Test fun standardLunchAffectsForecastAndAlertsBeforeLunch() {
        val day = day(at(8)).copy(alertsEnabled = true)
        assertEquals(at(17, 30), day.milestone(480))
        assertEquals(at(19, 30), day.milestone(600))
        assertEquals(listOf(at(19, 20), at(19, 30)), alertEvents(day, at(9)).map { it.at })
        assertEquals(60L, day.workedMinutes(at(9)))
        assertEquals(0L, day.breakMinutes(at(9)))
    }

    @Test fun automaticLunchPausesElapsedTimeAndResumesIt() {
        val day = day(at(8))
        assertEquals(210L, day.workedMinutes(at(12)))
        assertEquals(30L, day.breakMinutes(at(12)))
        assertTrue(day.inStandardBreak(at(11, 30)))
        assertFalse(day.inStandardBreak(at(13)))
        assertEquals(270L, day.workedMinutes(at(14)))
        assertEquals(90L, day.breakMinutes(at(14)))
    }

    @Test fun identicalManualLunchIsNotCountedTwice() {
        val day = day(at(8), at(11, 30), at(13))
        assertEquals(at(17, 30), day.milestone(480))
        assertEquals(480L, day.workedMinutes(at(17, 30)))
        assertEquals(90L, day.breakMinutes(at(17, 30)))
    }

    @Test fun overlappingManualLunchUsesUnionAndExtraBreakStillCounts() {
        val day = day(at(8), at(12), at(13, 30), at(15), at(15, 15))
        assertEquals(at(18, 15), day.milestone(480))
        assertEquals(135L, day.breakMinutes(at(18, 15)))
    }

    @Test fun earlyExitOnlySubtractsElapsedLunch() {
        val morning = day(at(8), at(11)).copy(finished = true)
        assertEquals(180L, morning.workedMinutes(at(20)))
        assertEquals(0L, morning.breakMinutes(at(20)))
        val duringLunch = day(at(8), at(12)).copy(finished = true)
        assertEquals(210L, duringLunch.workedMinutes(at(20)))
        assertEquals(30L, duringLunch.breakMinutes(at(20)))
    }

    @Test fun lateEntryAndOptOutDoNotSubtractUnworkedLunch() {
        assertEquals(at(21), day(at(13)).milestone(480))
        assertEquals(at(21), day(at(12)).milestone(480))
        assertEquals(at(16), day(at(8)).copy(standardBreak = false).milestone(480))
        assertNull(day(at(8)).nextDay().standardBreak)
    }

    @Test fun openManualPauseDoesNotForecastUnknownReturn() {
        val day = day(at(8), at(11))
        assertNull(day.milestone(480))
        assertEquals(180L, day.workedMinutes(at(14)))
        assertEquals(180L, day.breakMinutes(at(14)))
        assertEquals(at(11), day.milestone(180))
    }
}
