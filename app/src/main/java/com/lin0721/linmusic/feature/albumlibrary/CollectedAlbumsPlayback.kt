package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlin.random.Random

/** Shuffle album blocks, never individual songs; all writes remain in PlaybackController. */
internal object CollectedAlbumsPlayback {
    fun orderedAlbums(albums: List<AlbumSubItem>, shuffleAlbums: Boolean, random: Random = Random.Default): List<AlbumSubItem> {
        require(albums.all { it.id > 0L }) { "专辑标识无效，请刷新后重试" }
        val unique = albums.distinctBy { it.id }
        return if (shuffleAlbums) unique.shuffled(random) else unique
    }

    suspend fun prepare(
        albums: List<AlbumSubItem>,
        shuffleAlbums: Boolean,
        loadAlbum: suspend (AlbumSubItem) -> List<Track>,
        random: Random = Random.Default,
        onPrepared: (completed: Int, total: Int) -> Unit = { _, _ -> },
        onSkipped: ((AlbumSubItem) -> Unit)? = null
    ): List<QueueItem> = coroutineScope {
        val ordered = orderedAlbums(albums, shuffleAlbums, random)
        require(ordered.isNotEmpty()) { "当前没有可播放的专辑" }
        val requests = Semaphore(3)
        val progress = Mutex()
        var completed = 0
        // awaitAll preserves input order even when a later album finishes first.
        ordered.map { album -> async {
            requests.withPermit {
                try {
                val tracks = withTimeout(30_000) { loadAlbum(album) }
                currentCoroutineContext().ensureActive()
                check(tracks.isNotEmpty()) { "《${album.name}》没有可播放曲目，请打开专辑检查后重试" }
                check(tracks.all { it.id > 0L }) { "《${album.name}》曲目信息不完整，请重试" }
                val queue = tracks.map { track ->
                    QueueItem(songId = track.id, title = track.name,
                        artist = track.ar.joinToString(" / ") { it.name }.ifBlank { album.artists.joinToString(" / ") { it.name } },
                        coverUrl = track.al.picUrl.ifBlank { album.picUrl })
                }
                queue
                } catch (timeout: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    if (onSkipped == null) throw timeout
                    progress.withLock { onSkipped(album) }
                    emptyList()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    if (onSkipped == null) throw error
                    progress.withLock { onSkipped(album) }
                    emptyList()
                } finally {
                    progress.withLock { onPrepared(++completed, ordered.size) }
                }
            }
        } }.awaitAll().flatten().also { check(it.isNotEmpty()) { "所选专辑均未能读取，请打开专辑检查或刷新收藏后重试" } }
    }

    suspend fun loadTracks(album: AlbumSubItem, repository: PlaylistRepository): List<Track> {
        val detail = repository.getAlbumDetail(album.id).first().getOrThrow()
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
