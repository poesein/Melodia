package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.download.toToastMessage
import com.lin0721.linmusic.core.download.yearFromEpochMillis
import com.lin0721.linmusic.core.model.isLikedSongsPlaylist
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.first

// 歌曲多选后的批量操作：歌单页与歌手页共用，结果文案经 onToast 回给调用方
class TrackBatchActionsDelegate(
    private val playlistRepository: PlaylistRepository,
    private val playerManager: PlaybackController,
    private val songDownloadManager: SongDownloader,
    private val songLikeRepository: SongLikeRepository,
    private val createPlaylistAndAddSongUseCase: CreatePlaylistAndAddSongUseCase,
    private val userPlaylistRepository: UserPlaylistRepository,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider
) {

    private fun Track.toQueueItem() = QueueItem(id, name, ar.joinToString("/") { it.name }, al.picUrl)

    private fun Track.toDownloadInfo() = DownloadTrackInfo(
        id, name, ar.joinToString("/") { it.name },
        al.name, al.picUrl.takeIf { it.isNotBlank() },
        yearFromEpochMillis(publishTime)
    )

    // 不可播放的歌曲不入队；全部不可播放时不做任何操作
    suspend fun playNext(tracks: List<Track>, unplayableIds: Set<Long>, onToast: suspend (String) -> Unit): Boolean {
        val playable = tracks.filter { it.id !in unplayableIds }
        if (playable.isEmpty()) {
            onToast("所选歌曲暂无可播放的版本")
            return false
        }
        playerManager.addToPlayNext(playable.map { it.toQueueItem() })
        val skipped = tracks.size - playable.size
        onToast(
            if (skipped > 0) "已添加 ${playable.size} 首歌曲至下一首播放，跳过 $skipped 首不可播放"
            else "已添加 ${playable.size} 首歌曲至下一首播放"
        )
        return true
    }

    suspend fun addToPlaylist(targetPlaylistId: Long, tracks: List<Track>, onToast: suspend (String) -> Unit): Boolean {
        if (tracks.isEmpty()) return false
        return playlistRepository.manipulatePlaylistTracks("add", targetPlaylistId, tracks.map { it.id }).first().fold(
            onSuccess = {
                SongCollectDelegate.tracksChanged(targetPlaylistId, added = tracks.map { it.id }.toSet())
                onToast("已添加 ${tracks.size} 首歌曲到歌单")
                true
            },
            onFailure = {
                onToast(it.toUserMessage(resourceProvider))
                false
            }
        )
    }

    suspend fun createPlaylistAndAdd(
        name: String,
        privacy: Int,
        tracks: List<Track>,
        onToast: suspend (String) -> Unit
    ): Boolean {
        if (tracks.isEmpty()) return false
        return createPlaylistAndAddSongUseCase(name, tracks.map { it.id }, privacy).first().fold(
            onSuccess = {
                onToast("已创建歌单并添加 ${tracks.size} 首歌曲")
                true
            },
            onFailure = {
                onToast(it.toUserMessage(resourceProvider))
                false
            }
        )
    }

    suspend fun download(
        tracks: List<Track>,
        level: String,
        batchTag: String,
        batchLabel: String,
        onToast: suspend (String) -> Unit
    ): Boolean {
        if (tracks.isEmpty()) return false
        val result = songDownloadManager.enqueueBatch(tracks.map { it.toDownloadInfo() }, level, batchTag, batchLabel)
        onToast(result.toToastMessage())
        return true
    }

    suspend fun like(tracks: List<Track>, onToast: suspend (String) -> Unit): Int =
        setLiked(tracks, true, onToast = onToast).succeededIds.size

    suspend fun setLiked(
        tracks: List<Track>,
        liked: Boolean,
        force: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onApplied: (Long) -> Unit = {},
        shouldStop: () -> Boolean = { false },
        onToast: suspend (String) -> Unit
    ): BatchLikeResult {
        val ids = tracks.map { it.id }.filter { it > 0L }.distinct()
        val unchanged = if (force) emptySet() else ids.filter { (it in songLikeRepository.likedSongIds.value) == liked }.toSet()
        val targets = ids.filter { it !in unchanged }
        val succeeded = linkedSetOf<Long>()
        val failed = linkedSetOf<Long>()
        var firstError: Throwable? = null
        onProgress(unchanged.size, ids.size)
        for (id in targets) {
            if (shouldStop()) break
            val result = songLikeRepository.likeSong(id, liked).first()
            if (result.isSuccess) { succeeded += id; onApplied(id) }
            else { failed += id; if (firstError == null) firstError = result.exceptionOrNull() }
            onProgress(unchanged.size + succeeded.size + failed.size, ids.size)
            if (firstError in setOf(AppError.RiskControl, AppError.NetworkError, AppError.Unauthorized)) break
        }
        val unprocessed = (targets - succeeded - failed).toSet()
        val verb = if (liked) "喜欢" else "取消喜欢"
        onToast(when {
            targets.isEmpty() -> if (liked) "所选歌曲都已在我喜欢的音乐中" else "所选歌曲均未标记喜欢"
            failed.isEmpty() && unprocessed.isEmpty() -> "已$verb ${succeeded.size} 首歌曲"
            succeeded.isEmpty() && firstError != null -> firstError.toUserMessage(resourceProvider)
            unprocessed.isNotEmpty() -> "已$verb ${succeeded.size} 首，${failed.size + unprocessed.size} 首未完成，已保留选择"
            else -> "已$verb ${succeeded.size} 首歌曲，${failed.size} 首失败"
        })
        return BatchLikeResult(succeeded, unchanged, failed + unprocessed)
    }

    suspend fun removeFromPlaylist(playlistId: Long, tracks: List<Track>, onToast: suspend (String) -> Unit): Boolean {
        if (tracks.isEmpty()) return false
        return playlistRepository.manipulatePlaylistTracks("del", playlistId, tracks.map { it.id }).first().fold(
            onSuccess = {
                SongCollectDelegate.tracksChanged(playlistId, removed = tracks.map { it.id }.toSet())
                onToast("已从歌单中删除 ${tracks.size} 首歌曲")
                true
            },
            onFailure = {
                onToast(it.toUserMessage(resourceProvider))
                false
            }
        )
    }

    // 当前用户自建、可作为添加目标的歌单；排除指定歌单与"我喜欢的音乐"。未登录或失败时返回 null
    suspend fun loadTargetPlaylists(excludePlaylistId: Long?, onToast: suspend (String) -> Unit): List<UserPlaylist>? {
        val profile = userPreferences.userProfile.first()
        if (profile == null) {
            onToast("请先登录账号")
            return null
        }
        return userPlaylistRepository.getUserPlaylists(profile.uid).first().fold(
            onSuccess = { playlists ->
                playlists.filter { it.userId == profile.uid && it.id != excludePlaylistId && !isLikedSongsPlaylist(it.name, it.id, profile.uid) }
            },
            onFailure = {
                onToast(it.toUserMessage(resourceProvider))
                null
            }
        )
    }
}

/** Successful and unchanged items can be deselected; remaining items stay retryable. */
data class BatchLikeResult(val succeededIds: Set<Long>, val unchangedIds: Set<Long>, val remainingIds: Set<Long>) {
    val complete: Boolean get() = remainingIds.isEmpty()
}
