package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.format.totalKnownDurationMs
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Fetch missing metadata only, keeping pagination and the displayed track list unchanged. */
class PlaylistDurationCalculator(private val loadTracks: suspend (List<Long>) -> List<Track>) {
    private val durations = mutableMapOf<Long, Long>()

    suspend fun totalFor(detail: PlaylistDetail): Long? {
        detail.tracks.filter { it.dt > 0 }.forEach { durations[it.id] = it.dt }
        val ids = if (detail.trackIds.isEmpty()) detail.tracks.map { it.id } else detail.trackIds.map { it.id }
        val missing = ids.distinct().filter { it !in durations }
        for (chunk in missing.chunked(500)) {
            val requested = chunk.toSet()
            val tracks = loadTracks(chunk)
            currentCoroutineContext().ensureActive()
            tracks.filter { it.id in requested && it.dt > 0 }.forEach { durations[it.id] = it.dt }
        }
        return totalKnownDurationMs(ids.map { durations[it] ?: 0L })
    }
}
