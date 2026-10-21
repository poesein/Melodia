package com.lin0721.linmusic.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    @Test fun `collection duration has optional hours and explicit label`() {
        assertEquals("0:00 时长", collectionDurationLabel(0))
        assertEquals("32:15 时长", collectionDurationLabel(1_935_000))
        assertEquals("1:00:00 时长", collectionDurationLabel(3_600_000))
        assertEquals("1:32:15 时长", collectionDurationLabel(5_535_000))
        assertEquals("--:-- 时长", collectionDurationLabel(null))
        assertEquals("0:00", formatPlaybackDuration(-1000))
        assertEquals("100:00:00", formatPlaybackDuration(360_000_000))
    }

    @Test fun `missing metadata never becomes a misleading partial sum`() {
        assertEquals(0L, totalKnownDurationMs(emptyList()))
        assertEquals(5_535_000L, totalKnownDurationMs(listOf(1_935_000, 3_600_000)))
        assertEquals(null, totalKnownDurationMs(listOf(1000, 0)))
        assertEquals(null, totalKnownDurationMs(listOf(-1, 1000)))
        assertEquals(Long.MAX_VALUE, totalKnownDurationMs(listOf(Long.MAX_VALUE, 1000)))
    }

    @Test
    fun `按分钟和两位秒数显示`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:05", formatDuration(5_999))
        assertEquals("3:07", formatDuration(187_000))
    }

    @Test
    fun `超过一小时仍按分钟累计`() {
        assertEquals("61:01", formatDuration(3_661_000))
    }

    @Test
    fun `负数按零处理`() {
        assertEquals("0:00", formatDuration(-1_000))
    }
}
