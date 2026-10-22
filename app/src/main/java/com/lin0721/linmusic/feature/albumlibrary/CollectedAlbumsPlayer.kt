package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Starts with one album, keeps just the next album ready, and survives page navigation. */
class CollectedAlbumsPlayer internal constructor(
    private val controller: PlaybackController,
    private val preferences: UserPreferences,
    private val scope: CoroutineScope,
    private val notify: (String) -> Unit
) {
    private var continuation: Job? = null
    private var generation = 0L

    suspend fun start(
        albums: List<AlbumSubItem>,
        shuffle: Boolean,
        account: Long,
        load: suspend (AlbumSubItem) -> List<QueueItem>
    ) {
        val request = ++generation
        continuation?.cancel()
        val ordered = CollectedAlbumsPlayback.orderedAlbums(albums, shuffle)
        require(ordered.isNotEmpty()) { "当前没有可播放的专辑" }
        val token = UUID.randomUUID().toString()
        suspend fun read(album: AlbumSubItem): List<QueueItem> {
            val items = withTimeout(60_000) { load(album) }
            check(items.isNotEmpty() && items.all { it.songId > 0L }) { "《${album.name}》曲目信息不完整" }
            return items.map { it.copy(albumSessionId = token) }
        }
        val first = read(ordered.first())
        withTimeout(15_000) { controller.initController() }
        currentCoroutineContext().ensureActive()
        if (generation != request || preferences.userProfile.first()?.uid != account) throw CancellationException()
        CollectedAlbumsPlayback.useAlbumOrder(controller)
        val source = ordered.singleOrNull()?.let { PlaySource(PlaySource.Kind.ALBUM, it.id, it.name, it.picUrl) }
        controller.playQueue(first, 0, "收藏专辑", source)
        if (ordered.size == 1) return
        continuation = scope.launch {
            var expected = first
            var activeAlbumStart = 0
            fun ownsQueue() = generation == request && controller.queue.value == expected &&
                controller.playContext.value == "收藏专辑" && controller.playMode.value == PlayMode.LIST_LOOP
            for (album in ordered.drop(1)) {
                // Abort on an account/queue change, including while the next request is suspended.
                val ready = combine(controller.currentIndex, controller.queue, controller.playContext,
                    controller.playMode, preferences.userProfile) { index, _, _, _, profile ->
                    !ownsQueue() || profile?.uid != account || index >= activeAlbumStart
                }.first { it }
                if (!ready || !ownsQueue() || preferences.userProfile.first()?.uid != account) break
                val items = try { read(album) }
                catch (cancelled: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    if (ownsQueue()) notify("《${album.name}》读取超时，已跳过；当前专辑继续播放")
                    continue
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (ownsQueue()) notify("《${album.name}》读取失败，已跳过；当前专辑继续播放")
                    continue
                }
                currentCoroutineContext().ensureActive()
                if (!ownsQueue() || preferences.userProfile.first()?.uid != account) break
                activeAlbumStart = expected.size
                expected = expected + items
                controller.appendToQueue(items)
            }
        }
    }
}
