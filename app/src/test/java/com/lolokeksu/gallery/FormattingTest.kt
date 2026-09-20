package com.lolokeksu.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test fun durationSeconds() { assertEquals("0:09", formatDuration(9000)) }
    @Test fun durationMinutes() { assertEquals("2:05", formatDuration(125000)) }
    @Test fun durationZero() { assertEquals("0:00", formatDuration(0)) }
    @Test fun durationHours() { assertEquals("1:05:07", formatDuration(3907000)) }
    @Test fun durationExactHour() { assertEquals("1:00:00", formatDuration(3600000)) }
    @Test fun durationNegativeIsClamped() { assertEquals("0:00", formatDuration(-5000)) }
}
