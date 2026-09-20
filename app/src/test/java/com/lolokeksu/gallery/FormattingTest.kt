package com.lolokeksu.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test fun durationSeconds() { assertEquals("0:09", formatDuration(9000)) }
    @Test fun durationMinutes() { assertEquals("2:05", formatDuration(125000)) }
    @Test fun durationZero() { assertEquals("0:00", formatDuration(0)) }
}
