package com.lin0721.linmusic.core.player

/** Resolving the next source must never advance the live queue. Edits invalidate this snapshot. */
data class GaplessQueueSnapshot(
    val items: List<QueueItem>,
    val index: Int,
    val mode: PlayMode,
    val context: String?
) {
    val nextIndex: Int get() = if (items.size > 1 && index in items.indices && mode != PlayMode.SINGLE_LOOP)
        (index + 1) % items.size else -1
    fun isCurrent(queue: PlaybackQueue): Boolean = items == queue.items.value &&
        index == queue.currentIndex.value && mode == queue.playMode.value && context == queue.playContext.value
}
