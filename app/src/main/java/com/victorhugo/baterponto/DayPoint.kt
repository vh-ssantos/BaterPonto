package com.victorhugo.baterponto

import java.time.LocalDateTime

/** Null index identifies an automatic lunch boundary, not a manually recorded punch. */
data class DayPoint(val time: LocalDateTime, val label: String, val punchIndex: Int?)
