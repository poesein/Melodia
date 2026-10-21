package com.lin0721.linmusic.feature.playlist

import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.playlist.domain.PlaylistDurationCalculator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaylistDurationCalculatorTest {
    @Test fun `sum complete list beyond loaded page and reuse cached metadata`() = runBlocking {
        val requests = mutableListOf<List<Long>>()
        val calculator = PlaylistDurationCalculator { ids ->
            requests += ids
            ids.map { Track(id = it, dt = 1000) }
        }
        val detail = PlaylistDetail(tracks = listOf(Track(id = 1, dt = 2000)),
            trackIds = (1L..1201L).map { PlaylistTrackId(it) })
        assertEquals(1_202_000L, calculator.totalFor(detail))
        assertEquals(listOf(500, 500, 200), requests.map { it.size })
        assertFalse(requests.flatten().contains(1))
        assertEquals(1_202_000L, calculator.totalFor(detail.copy(tracks = emptyList())))
        assertEquals(3, requests.size)
    }
    @Test fun `unavailable metadata stays unknown and can retry`() = runBlocking {
        var available = false
        val calculator = PlaylistDurationCalculator { ids ->
            if (available) ids.map { Track(id = it, dt = 2000) } else listOf(Track(id = 999, dt = 5000))
        }
        val detail = PlaylistDetail(tracks = listOf(Track(id = 1, dt = 1000)),
            trackIds = listOf(PlaylistTrackId(1), PlaylistTrackId(2)))
        assertNull(calculator.totalFor(detail))
        available = true
        assertEquals(3000L, calculator.totalFor(detail))
    }
    @Test fun `duplicates count in order and removed tracks do not remain in total`() = runBlocking {
        val calculator = PlaylistDurationCalculator { error("all tracks already loaded") }
        val detail = PlaylistDetail(tracks = listOf(Track(id = 1, dt = 2000), Track(id = 2, dt = 3000)),
            trackIds = listOf(PlaylistTrackId(1), PlaylistTrackId(2), PlaylistTrackId(1)))
        assertEquals(7000L, calculator.totalFor(detail))
        assertEquals(2000L, calculator.totalFor(detail.copy(trackIds = listOf(PlaylistTrackId(1)))))
        assertEquals(5000L, calculator.totalFor(detail.copy(trackIds = emptyList())))
        assertEquals(0L, calculator.totalFor(PlaylistDetail()))
    }
}
