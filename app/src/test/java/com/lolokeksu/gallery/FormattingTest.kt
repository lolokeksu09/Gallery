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

    @Test fun oneFile() { assertEquals("1 файл", fileCount(1)) }
    @Test fun fewFiles() { assertEquals("3 файла", fileCount(3)) }
    @Test fun manyFiles() { assertEquals("7 файлов", fileCount(7)) }
    @Test fun noFiles() { assertEquals("0 файлов", fileCount(0)) }
    // Eleven to fourteen are the exception the last digit alone gets wrong.
    @Test fun eleven() { assertEquals("11 файлов", fileCount(11)) }
    @Test fun twelve() { assertEquals("12 файлов", fileCount(12)) }
    @Test fun fourteen() { assertEquals("14 файлов", fileCount(14)) }
    @Test fun twentyOne() { assertEquals("21 файл", fileCount(21)) }
    @Test fun twentyThree() { assertEquals("23 файла", fileCount(23)) }
    @Test fun hundredOne() { assertEquals("101 файл", fileCount(101)) }
    @Test fun hundredEleven() { assertEquals("111 файлов", fileCount(111)) }
    @Test fun oneHundredEightyFour() { assertEquals("184 файла", fileCount(184)) }
}
