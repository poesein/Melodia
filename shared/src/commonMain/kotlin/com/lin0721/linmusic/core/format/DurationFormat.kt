package com.lin0721.linmusic.core.format

import java.util.Locale

// 歌曲时长与播放进度，m:ss；超过一小时仍按分钟累计，负数按 0 处理
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

// Collection totals retain seconds; show hours only when needed.
fun formatPlaybackDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0L)
    return if (seconds >= 3600L) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

/** Unknown track lengths must never be presented as a complete collection total. */
fun totalKnownDurationMs(durations: Iterable<Long>): Long? {
    var total = 0L
    for (duration in durations) {
        if (duration <= 0L) return null
        total = if (Long.MAX_VALUE - total < duration) Long.MAX_VALUE else total + duration
    }
    return total
}

fun collectionDurationLabel(totalMs: Long?): String = "${totalMs?.let(::formatPlaybackDuration) ?: "--:--"} 时长"
