package com.lin0721.linmusic.core.player

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** Prepares a verified successor silently, without replacing the active player's visible timeline. */
@OptIn(UnstableApi::class)
internal class PreparedCrossfade(
    private val handler: Handler,
    private val active: () -> ExoPlayer,
    private val standby: () -> ExoPlayer,
    private val canPrepare: () -> Boolean,
    private val activate: (ExoPlayer, Long) -> Unit
) {
    private data class Stage(val expectedId: String, val generation: Long, val item: MediaItem,
        val triggerMs: Long, val fadeMs: Long, val startMs: Long)
    private var stage: Stage? = null
    private var prepared: ExoPlayer? = null

    fun stage(expectedId: String, generation: Long, item: MediaItem, triggerMs: Long, fadeMs: Long, startMs: Long): Boolean {
        if (active().currentMediaItem?.mediaId != expectedId || fadeMs <= 0L) return false
        val previous = stage
        if (previous?.generation != generation || previous.item != item) cancel()
        stage = Stage(expectedId, generation, item, triggerMs.coerceAtLeast(0), fadeMs, startMs.coerceAtLeast(0))
        if (prepared != null && previous?.startMs != startMs) prepared?.seekTo(startMs.coerceAtLeast(0))
        handler.removeCallbacks(tick)
        handler.post(tick)
        return true
    }

    fun cancel() {
        handler.removeCallbacks(tick)
        stage = null
        val cancelled = prepared
        prepared = null
        cancelled?.apply { stop(); clearMediaItems(); volume = 1f }
    }

    private val tick = object : Runnable {
        override fun run() {
            val plan = stage ?: return
            val current = active()
            if (current.currentMediaItem?.mediaId != plan.expectedId || !current.playWhenReady) { cancel(); return }
            if (prepared == null && canPrepare()) {
                prepared = standby().apply {
                    stop()
                    volume = 0f
                    playWhenReady = false
                    repeatMode = current.repeatMode
                    setMediaItem(plan.item, plan.startMs)
                    prepare()
                }
            }
            val next = prepared
            if (next?.playerError != null) { cancel(); return }
            // A long user setting must not consume most of a short intro/interlude. Once the
            // successor's duration is known, shorten the fade and move its start towards the same
            // outgoing end point, leaving the spare player free before the following transition.
            val nextAvailableMs = next?.duration?.takeIf { it > 0L }?.minus(plan.startMs)?.coerceAtLeast(0L)
            val fadeMs = nextAvailableMs?.let { minOf(plan.fadeMs, (it / 2).coerceAtLeast(1L)) } ?: plan.fadeMs
            val triggerMs = plan.triggerMs + (plan.fadeMs - fadeMs)
            val ended = current.playbackState == Player.STATE_ENDED
            val due = current.isPlaying && current.currentPosition >= triggerMs
            if (next != null && (ended || (due && next.playbackState == Player.STATE_READY))) {
                stage = null
                prepared = null // Ownership passes to CrossfadePlayer; cancellation must not stop it.
                val remaining = (current.duration - current.currentPosition).coerceAtLeast(0)
                activate(next, if (ended) 0 else minOf(fadeMs, remaining))
                return
            }
            val untilTrigger = triggerMs - current.currentPosition
            handler.postDelayed(this, if (untilTrigger in 1..1000) minOf(10L, untilTrigger) else 100L)
        }
    }
}
