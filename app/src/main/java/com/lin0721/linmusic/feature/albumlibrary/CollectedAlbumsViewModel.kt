package com.lin0721.linmusic.feature.albumlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class CollectedAlbumsState(
    val albums: List<AlbumSubItem> = emptyList(),
    val loading: Boolean = true,
    val loggedIn: Boolean = false,
    val error: String? = null,
    val preparing: Boolean = false,
    val preparedAlbums: Int = 0,
    val totalAlbums: Int = 0,
    val loadingReleaseDates: Boolean = false
)

/** Account and request tokens prevent a cancelled predecessor from replacing the visible list/queue. */
class CollectedAlbumsViewModel(
    private val repository: CollectedAlbumsRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackController: PlaybackController,
    private val userPreferences: UserPreferences
) : ViewModel() {
    private val mutableState = MutableStateFlow(CollectedAlbumsState())
    val state: StateFlow<CollectedAlbumsState> = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = mutableEvents.asSharedFlow()
    private var uid: Long? = null
    private var accountGeneration = 0L
    private var listGeneration = 0L
    private var playbackGeneration = 0L
    private var listJob: Job? = null
    private var playbackJob: Job? = null
    private var releaseDatesJob: Job? = null
    private val releaseTimes = mutableMapOf<Long, Long>()

    fun ensureReleaseDates() {
        if (releaseDatesJob?.isActive == true || state.value.loading) return
        val selected = state.value.albums.filter { it.publishTime <= 0L && it.id !in releaseTimes }
        if (selected.isEmpty()) return
        val context = accountGeneration
        mutableState.update { it.copy(loadingReleaseDates = true) }
        releaseDatesJob = viewModelScope.launch {
            try {
                val limits = Semaphore(3)
                val dates = coroutineScope { selected.map { album -> async {
                    limits.withPermit {
                        val time = try { withTimeout(15_000) { playlistRepository.getAlbumDetail(album.id).first().getOrThrow().publishTime } }
                        catch (cancelled: TimeoutCancellationException) { 0L }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { 0L }
                        album.id to time
                    }
                } }.awaitAll().toMap() }
                if (context == accountGeneration) {
                    releaseTimes.putAll(dates)
                    mutableState.update { current -> current.copy(albums = current.albums.map { album ->
                        album.copy(publishTime = dates[album.id]?.takeIf { it > 0L } ?: album.publishTime)
                    }) }
                    if (dates.values.any { it <= 0L }) mutableEvents.tryEmit("部分专辑发行时间未能取得，已排在末尾；刷新后可重试")
                }
            } finally {
                if (context == accountGeneration) mutableState.update { it.copy(loadingReleaseDates = false) }
            }
        }
    }

    init {
        viewModelScope.launch {
            userPreferences.userProfile.map { it?.uid?.takeIf { id -> id > 0L } }.distinctUntilChanged()
                .catch { error -> mutableState.value = CollectedAlbumsState(loading = false, error = message(error)) }
                .collect { account ->
                    accountGeneration++
                    listGeneration++
                    listJob?.cancel()
                    releaseDatesJob?.cancel()
                    releaseTimes.clear()
                    cancelPlaybackPreparation()
                    uid = account
                    mutableState.value = CollectedAlbumsState(loading = account != null, loggedIn = account != null)
                    if (account != null) load(account)
                }
        }
    }

    fun refresh() {
        val account = uid ?: return
        releaseTimes.entries.removeAll { it.value <= 0L }
        load(account)
    }

    private fun load(account: Long) {
        val generation = ++listGeneration
        val context = accountGeneration
        listJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        listJob = viewModelScope.launch {
            try {
                requireAccount(account, context)
                repository.load(account).collect { result ->
                    requireAccount(account, context)
                    if (generation != listGeneration) return@collect
                    result.fold(onSuccess = { albums ->
                        mutableState.update { it.copy(albums = albums.map { album -> album.copy(
                            publishTime = releaseTimes[album.id]?.takeIf { time -> time > 0L } ?: album.publishTime)
                        }, loading = false, error = null) }
                    }, onFailure = { error ->
                        mutableState.update { it.copy(loading = false, error = message(error)) }
                    })
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation == listGeneration && context == accountGeneration) {
                    mutableState.update { it.copy(loading = false, error = message(error)) }
                }
            }
        }
    }

    fun play(albums: List<AlbumSubItem>, shuffleAlbums: Boolean) {
        val account = uid ?: run { mutableEvents.tryEmit("请先登录后播放收藏专辑"); return }
        val selected = albums.toList().distinctBy { it.id }
        if (selected.isEmpty()) { mutableEvents.tryEmit("当前没有可播放的专辑"); return }
        cancelPlaybackPreparation()
        val generation = playbackGeneration
        val context = accountGeneration
        mutableState.update { it.copy(preparing = true, preparedAlbums = 0, totalAlbums = selected.size) }
        playbackJob = viewModelScope.launch {
            try {
                requireAccount(account, context)
                val skipped = mutableListOf<AlbumSubItem>()
                val queue = CollectedAlbumsPlayback.prepare(selected, shuffleAlbums,
                    loadAlbum = { CollectedAlbumsPlayback.loadTracks(it, playlistRepository) },
                    onPrepared = { count, total ->
                        if (generation == playbackGeneration && context == accountGeneration) {
                            mutableState.update { it.copy(preparedAlbums = count, totalAlbums = total) }
                        }
                    }, onSkipped = { skipped += it })
                withTimeout(15_000) { playbackController.initController() }
                requireAccount(account, context)
                if (generation != playbackGeneration) return@launch
                // Commit once, through the existing resolver (local links/cache/strict source mode).
                // Switching the engine's song shuffle off is essential for intact album blocks.
                CollectedAlbumsPlayback.useAlbumOrder(playbackController)
                val source = selected.singleOrNull()?.let { PlaySource(PlaySource.Kind.ALBUM, it.id, it.name, it.picUrl) }
                playbackController.playQueue(queue, 0, playContext = "收藏专辑", source = source)
                if (skipped.isNotEmpty()) mutableEvents.tryEmit("已播放可读取的专辑；跳过 ${skipped.size} 张：${skipped.take(3).joinToString { it.name }}")
            } catch (_: TimeoutCancellationException) {
                if (generation == playbackGeneration && context == accountGeneration) mutableEvents.tryEmit("读取专辑或连接播放器超时，请重试")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation == playbackGeneration && context == accountGeneration) mutableEvents.tryEmit(message(error))
            } finally {
                if (generation == playbackGeneration && context == accountGeneration) {
                    mutableState.update { it.copy(preparing = false) }
                }
            }
        }
    }

    fun cancelPlaybackPreparation() {
        playbackGeneration++
        playbackJob?.cancel()
        playbackJob = null
        mutableState.update { it.copy(preparing = false, preparedAlbums = 0, totalAlbums = 0) }
    }

    private suspend fun requireAccount(account: Long, context: Long) {
        currentCoroutineContext().ensureActive()
        if (context != accountGeneration || uid != account || userPreferences.userProfile.first()?.uid != account) {
            throw CancellationException("登录账户已改变")
        }
        currentCoroutineContext().ensureActive()
    }

    private fun message(error: Throwable): String = when (error) {
        AppError.Unauthorized -> "登录状态已失效，请重新登录"
        AppError.NetworkError -> "无法连接网络，请重试"
        AppError.RiskControl -> "请求暂时受限，请稍后重试"
        AppError.ParseError -> "专辑信息格式异常，请稍后重试"
        is AppError.BizError -> "读取专辑失败，请稍后重试"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "无法读取专辑，请重试"
    }
}
