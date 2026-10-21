package com.lin0721.linmusic.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** Use the active player's native playlist: no decoder reset or artificial overlap at the boundary. */
internal object GaplessPlaylist {
    fun append(player: Player, expectedId: String, next: MediaItem): Boolean {
        if (player.playbackState != Player.STATE_READY || player.currentMediaItem?.mediaId != expectedId) return false
        clearUpcoming(player)
        player.addMediaItem(player.currentMediaItemIndex + 1, next)
        return true
    }

    fun clearUpcoming(player: Player) {
        val start = player.currentMediaItemIndex + 1
        if (start in 0 until player.mediaItemCount) player.removeMediaItems(start, player.mediaItemCount)
    }

    fun trimConsumed(player: Player) {
        val index = player.currentMediaItemIndex
        if (index > 0) player.removeMediaItems(0, index)
    }
}
