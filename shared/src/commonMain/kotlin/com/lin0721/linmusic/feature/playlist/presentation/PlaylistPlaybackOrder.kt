package com.lin0721.linmusic.feature.playlist.presentation

/** Ordered playback must compare the actual sequence. Shuffle can resume the same membership. */
fun playlistQueueMatches(queueIds: List<Long>, orderedIds: List<Long>, shuffle: Boolean,
    allowLoadedPrefix: Boolean = false): Boolean {
    if (orderedIds.isEmpty()) return false
    if (!shuffle) return if (allowLoadedPrefix) queueIds.take(orderedIds.size) == orderedIds else queueIds == orderedIds
    val current = queueIds.groupingBy { it }.eachCount()
    val expected = orderedIds.groupingBy { it }.eachCount()
    return if (allowLoadedPrefix) expected.all { (id, count) -> current.getOrDefault(id, 0) >= count } else current == expected
}
