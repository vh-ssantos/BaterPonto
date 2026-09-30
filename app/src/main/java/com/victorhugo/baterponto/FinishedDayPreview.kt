package com.victorhugo.baterponto

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.victorhugo.baterponto.ui.theme.BaterPontoTheme
import java.time.LocalDate

@Preview(showBackground = true, widthDp = 393, heightDp = 1000)
@Composable
private fun FinishedDayPreview() {
    val date = LocalDate.of(2026, 9, 29)
    val day = WorkDay(standardBreak = true)
        .record(date.atTime(8, 21))
        .record(date.atTime(17, 47))
        .record(date.atTime(19, 50))
        .record(date.atTime(21, 50), finalExit = true)
    BaterPontoTheme(darkTheme = true) {
        WorkDayScreen(day, date.plusDays(1).atTime(7, 0), true, true, {}, {}, {}, {})
    }
}
