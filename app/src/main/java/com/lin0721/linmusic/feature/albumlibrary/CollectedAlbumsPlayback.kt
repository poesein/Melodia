package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/** Shuffle album blocks, never individual songs; all writes remain in PlaybackController. */
internal object CollectedAlbumsPlayback {
    fun orderedAlbums(albums: List<AlbumSubItem>, shuffleAlbums: Boolean, random: Random = Random.Default): List<AlbumSubItem> {
        require(albums.all { it.id > 0L }) { "专辑标识无效，请刷新后重试" }
        val unique = albums.distinctBy { it.id }
        return if (shuffleAlbums) unique.shuffled(random) else unique
    }

    fun queueItems(album: AlbumSubItem, tracks: List<Track>): List<QueueItem> = tracks.map { track ->
        QueueItem(songId = track.id, title = track.name,
            artist = track.ar.joinToString(" / ") { it.name }.ifBlank { album.artists.joinToString(" / ") { it.name } },
            coverUrl = track.al.picUrl.ifBlank { album.picUrl })
    }

    suspend fun loadTracks(album: AlbumSubItem, repository: PlaylistRepository, cachedDetail: PlaylistDetail? = null): List<Track> {
        val detail = cachedDetail ?: repository.getAlbumDetail(album.id).first().getOrThrow()
        check(detail.id == album.id) { "《${album.name}》返回了其他专辑的信息，请重试" }
        val declaredIds = detail.trackIds.map { it.id }.filter { it > 0L }
        val loaded = if (declaredIds.any { id -> detail.tracks.none { it.id == id } }) {
            repository.loadAllTracks(detail).first().getOrThrow()
        } else detail.tracks
        return originalTrackOrder(detail, loaded)
    }

    internal fun originalTrackOrder(detail: PlaylistDetail, loaded: List<Track>): List<Track> {
        if (detail.trackIds.isEmpty()) return loaded
        check(detail.trackIds.all { it.id > 0L }) { "专辑曲序信息无效，请重试" }
        val byId = loaded.associateBy { it.id }
        val declared = detail.trackIds.map { it.id }
        check(declared.all { it in byId }) { "专辑曲目没有读取完整，请重试" }
        val known = declared.toHashSet()
        return declared.map { byId.getValue(it) } + loaded.filter { it.id !in known }
    }

    fun useAlbumOrder(controller: PlaybackController) {
        when (controller.playMode.value) {
            PlayMode.SHUFFLE -> controller.toggleShuffle()
            PlayMode.SINGLE_LOOP -> controller.toggleRepeat()
            PlayMode.LIST_LOOP -> Unit
        }
        check(controller.playMode.value == PlayMode.LIST_LOOP) { "无法切换为专辑顺序播放，请重试" }
    }
}
