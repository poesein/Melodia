package com.lin0721.linmusic.core.player

import org.junit.Assert.*
import org.junit.Test

class GaplessQueueSnapshotTest {
    private fun item(id: Long) = QueueItem(id, "song", "artist", "")
    private fun PlaybackQueue.snapshot() = GaplessQueueSnapshot(items.value, currentIndex.value, playMode.value, playContext.value)

    @Test fun `wrap and duplicate ids use queue position`() {
        val queue = PlaybackQueue().apply { replaceAll(listOf(item(1), item(1), item(2)), 2) }
        assertEquals(0, queue.snapshot().nextIndex)
        queue.setCurrentIndex(0)
        val snapshot = queue.snapshot()
        assertEquals(1, snapshot.nextIndex)
        queue.setCurrentIndex(1)
        assertFalse(snapshot.isCurrent(queue))
    }
    @Test fun `edits invalidate a prepared successor`() {
        val queue = PlaybackQueue().apply { replaceAll(listOf(item(1), item(2), item(3)), 0) }
        val beforeInsert = queue.snapshot()
        queue.insertNext(listOf(item(4)))
        assertFalse(beforeInsert.isCurrent(queue))
        val beforeMove = queue.snapshot()
        queue.move(1, 3)
        assertFalse(beforeMove.isCurrent(queue))
        val beforeRemove = queue.snapshot()
        queue.removeAt(1)
        assertFalse(beforeRemove.isCurrent(queue))
        val beforeContext = queue.snapshot()
        queue.setPlayContext("album")
        assertFalse(beforeContext.isCurrent(queue))
    }
    @Test fun `single repeat and empty or single item queues do not stage next`() {
        val queue = PlaybackQueue()
        assertEquals(-1, queue.snapshot().nextIndex)
        queue.replaceAll(listOf(item(1)), 0)
        assertEquals(-1, queue.snapshot().nextIndex)
        queue.insertNext(listOf(item(2)))
        val old = queue.snapshot()
        queue.setPlayMode(PlayMode.SINGLE_LOOP)
        assertFalse(old.isCurrent(queue))
        assertEquals(-1, queue.snapshot().nextIndex)
    }
}
