package com.lin0721.linmusic.feature.library.textimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationEvent
import com.lin0721.linmusic.feature.create.data.CreateRepository
import com.lin0721.linmusic.feature.create.data.playlistPrivacyOf
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistApi
import com.lin0721.linmusic.feature.playlist.data.PlaylistDetailRequest
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class PlaylistTextImportState(
    val fileName: String = "",
    val name: String = "",
    val isPrivate: Boolean = true,
    val rows: List<TextPlaylistRow> = emptyList(),
    val loading: Boolean = false,
    val matching: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val activePlaylistId: Long? = null,
    val targetPlaylist: UserPlaylist? = null,
    val targetPlaylists: List<UserPlaylist> = emptyList(),
    val loadingTargets: Boolean = false,
    val targetError: String? = null,
    val finishedPlaylistId: Long? = null
) {
    val selectionLocked get() = saving || activePlaylistId != null
    val selectedCount get() = PlaylistTextDocument.selectedIds(rows).size
}

private class ImportAccountChanged : IllegalStateException()
private class ImportTargetUnavailable : IllegalStateException()

/** Import orchestration stays outside LibraryViewModel and reuses the normal search/write APIs. */
class PlaylistTextImportViewModel(
    private val searchRepository: SearchRepository,
    private val createRepository: CreateRepository,
    private val playlistRepository: PlaylistRepository,
    private val userPreferences: UserPreferences,
    private val playlistMutationBus: PlaylistMutationBus,
    private val playlistApi: PlaylistApi,
    private val userPlaylistRepository: UserPlaylistRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistTextImportState())
    val state = _state.asStateFlow()
    private var matchJob: Job? = null
    private var targetJob: Job? = null
    private var writer = PlaylistTextWriter()
    private var accountId: Long? = null

    fun reset() {
        if (_state.value.saving) return
        matchJob?.cancel()
        targetJob?.cancel()
        writer = PlaylistTextWriter()
        accountId = null
        _state.value = PlaylistTextImportState()
    }

    fun showReadError(message: String) { _state.update { it.copy(error = message) } }
    fun consumeFinished(id: Long) {
        _state.update { if (it.finishedPlaylistId == id) it.copy(finishedPlaylistId = null) else it }
    }
    fun setName(value: String) {
        if (!_state.value.selectionLocked) _state.update { it.copy(name = value.take(40)) }
    }
    fun setPrivate(value: Boolean) {
        if (!_state.value.selectionLocked) _state.update { it.copy(isPrivate = value) }
    }

    fun setTarget(playlist: UserPlaylist?) {
        if (_state.value.selectionLocked) return
        val selected = if (playlist == null) null else _state.value.targetPlaylists.firstOrNull { it.id == playlist.id } ?: return
        _state.update { it.copy(targetPlaylist = selected) }
    }

    fun loadTargets() {
        if (_state.value.selectionLocked || _state.value.loadingTargets) return
        _state.update { it.copy(loadingTargets = true, targetError = null) }
        targetJob = viewModelScope.launch {
            try {
                requireSameAccount()
                val uid = checkNotNull(accountId)
                val playlists = withTimeout(15_000) { userPlaylistRepository.getUserPlaylists(uid).first().getOrThrow() }
                requireSameAccount()
                _state.update { it.copy(targetPlaylists = playlists.filter { playlist ->
                    playlist.id > 0 && (playlist.creator?.userId?.takeIf { id -> id > 0 } ?: playlist.userId) == uid
                }.distinctBy { playlist -> playlist.id }) }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _state.update { it.copy(targetError = "读取歌单超时，请重试") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.update { it.copy(targetError = if (error is ImportAccountChanged) "登录账户已改变，请重新导入"
                    else "无法读取歌单，请检查网络后重试") }
            } finally { _state.update { it.copy(loadingTargets = false) } }
        }
    }

    fun loadDocument(bytes: ByteArray, fileName: String) = loadContent(fileName,
        fileName.substringBeforeLast('.', fileName).take(40)) {
        PlaylistTextDocument.parse(PlaylistTextDocument.decode(bytes))
    }

    fun loadText(text: String) = loadContent("粘贴的文本", "导入歌单") { PlaylistTextDocument.parse(text) }

    private fun loadContent(sourceName: String, playlistName: String, parseRows: () -> List<TextPlaylistRow>) {
        if (_state.value.selectionLocked) return
        val previous = matchJob
        val previousTargets = targetJob
        previous?.cancel()
        previousTargets?.cancel()
        targetJob = null
        matchJob = viewModelScope.launch {
            previous?.join()
            // Destination reads belong to the old preview/account too. Retire their result and
            // finally block before publishing a new session, so they cannot overwrite its targets.
            previousTargets?.join()
            _state.value = PlaylistTextImportState(fileName = sourceName, loading = true)
            writer = PlaylistTextWriter()
            accountId = null
            try {
                val rows = withContext(Dispatchers.Default) { parseRows() }
                ensureActive()
                accountId = userPreferences.userProfile.first()?.uid
                _state.value = PlaylistTextImportState(fileName = sourceName,
                    name = playlistName, rows = rows, matching = true)
                matchRows(rows.filter { it.status == TextPlaylistStatus.PENDING })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.update { it.copy(loading = false, matching = false,
                    error = (error as? IllegalArgumentException)?.message ?: "无法读取内容，请检查 TXT 文本格式") }
            }
        }
    }

    fun retryUnresolved() {
        if (_state.value.selectionLocked || _state.value.matching || _state.value.loading) return
        val rows = _state.value.rows.filter { it.status in setOf(TextPlaylistStatus.PENDING, TextPlaylistStatus.REVIEW, TextPlaylistStatus.NOT_FOUND, TextPlaylistStatus.ERROR) }
        if (rows.isEmpty()) return
        val previous = matchJob
        matchJob = viewModelScope.launch {
            previous?.join()
            _state.update { it.copy(matching = true, error = null) }
            matchRows(rows)
        }
    }

    fun stopMatching() {
        if (_state.value.saving || _state.value.loading) return
        matchJob?.cancel()
        _state.update { it.copy(matching = false) }
    }

    fun searchRow(line: Int, query: String) {
        if (_state.value.selectionLocked || _state.value.matching || query.isBlank()) return
        val row = _state.value.rows.firstOrNull { it.line == line } ?: return
        val previous = matchJob
        matchJob = viewModelScope.launch {
            previous?.join()
            _state.update { it.copy(matching = true, error = null) }
            try { matchRow(row, query.trim().take(200)) }
            finally { _state.update { it.copy(matching = false) } }
        }
    }

    fun select(line: Int, track: Track) {
        if (_state.value.selectionLocked || _state.value.matching) return
        updateRow(line) { row ->
            if (track.id <= 0 || row.candidates.none { it.id == track.id }) row
            else row.copy(title = track.name, artist = track.ar.joinToString(" / ") { it.name },
                selected = track, included = true, status = TextPlaylistStatus.MATCHED, message = "已手动选择")
        }
    }

    fun include(line: Int, included: Boolean) {
        if (_state.value.selectionLocked) return
        updateRow(line) { it.copy(included = included) }
    }

    private fun updateRow(line: Int, transform: (TextPlaylistRow) -> TextPlaylistRow) {
        _state.update { it.copy(rows = it.rows.map { row -> if (row.line == line) transform(row) else row }) }
    }

    private suspend fun matchRows(rows: List<TextPlaylistRow>) {
        try {
            val permits = Semaphore(2)
            coroutineScope {
                rows.map { row -> async { permits.withPermit {
                    matchRow(row, "${row.title} ${row.artist}", "${row.artist} ${row.title}")
                } } }.awaitAll()
            }
        } finally { _state.update { it.copy(matching = false) } }
    }

    private suspend fun matchRow(row: TextPlaylistRow, query: String, alternateQuery: String? = null) {
        var preview: TextPlaylistRow? = null
        val resolved = try {
            withTimeout(15_000) {
                suspend fun search(keywords: String): List<Track> = searchRepository
                    .search(keywords, SearchType.SONG, limit = 30).first().getOrThrow()
                    .items.filterIsInstance<SearchResultItem.SongItem>().map { it.track }
                val results = search(query)
                val first = PlaylistTextDocument.resolve(row, results)
                preview = first
                // The service may rank an artist's other songs above the requested title when
                // artist comes first. Retry the opposite order only for unresolved automatic rows.
                // Both calls share the original timeout/concurrency limits; manual queries stay literal.
                if (first.selected != null || alternateQuery.isNullOrBlank() || alternateQuery == query) first
                else PlaylistTextDocument.resolve(row, results + search(alternateQuery))
            }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            (preview ?: row).copy(status = TextPlaylistStatus.ERROR, selected = null, message = "搜索超时，可重试")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { (preview ?: row).copy(status = TextPlaylistStatus.ERROR, selected = null, message = "搜索失败，请检查网络后重试") }
        currentCoroutineContext().ensureActive()
        updateRow(row.line) { resolved.copy(included = it.included) }
    }

    private suspend fun requireSameAccount() {
        val current = userPreferences.userProfile.first()?.uid
        if (current == null || current != accountId) throw ImportAccountChanged()
    }

    fun save() {
        val snapshot = _state.value
        if (snapshot.saving || snapshot.matching || snapshot.loading || snapshot.finishedPlaylistId != null) return
        val ids = PlaylistTextDocument.selectedIds(snapshot.rows)
        if (ids.isEmpty() || snapshot.targetPlaylist == null && snapshot.name.isBlank()) {
            _state.update { it.copy(error = "请填写歌单名称，并至少选择一首匹配歌曲") }; return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                requireSameAccount()
                val id = writer.write(ids,
                    create = {
                        requireSameAccount()
                        val created = createRepository.createPlaylist(snapshot.name.trim(), playlistPrivacyOf(snapshot.isPrivate)).first().getOrThrow().id
                        if (created > 0) {
                            _state.update { it.copy(activePlaylistId = created) }
                            playlistMutationBus.emit(PlaylistMutationEvent.Created(created))
                        }
                        created
                    },
                    add = { id, missing ->
                        requireSameAccount()
                        playlistRepository.manipulatePlaylistTracks("add", id, missing).first().getOrThrow()
                    },
                    readIds = { id ->
                        requireSameAccount()
                        // Mutation reconciliation must use fresh, unfiltered server IDs. The regular
                        // detail repository can return an offline cache or omit blocked artists.
                        val response = withTimeout(15_000) { playlistApi.getPlaylistDetail(PlaylistDetailRequest(id)) }
                        val detail = response.playlist?.takeIf { response.isSuccess && it.id == id }
                            ?: error("无法从服务器核对歌单")
                        if (snapshot.targetPlaylist != null && detail.creator?.userId != accountId) throw ImportTargetUnavailable()
                        (detail.trackIds.map { it.id } + detail.tracks.map { it.id }).toSet()
                    }, existingPlaylistId = snapshot.targetPlaylist?.id)
                if (snapshot.targetPlaylist == null) playlistMutationBus.emit(PlaylistMutationEvent.Created(id))
                _state.update { it.copy(finishedPlaylistId = id) }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _state.update { it.copy(error = "核对服务器歌单超时，请检查网络后继续添加。") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.update { it.copy(error = if (error is ImportAccountChanged) "登录账户已改变，请关闭面板并重新导入"
                    else if (error is ImportTargetUnavailable) "目标歌单已不可用或不属于当前账户，请关闭面板后重新选择"
                    else if (writer.playlistId != null)
                    "添加歌曲未完成。重试会核对目标歌单并继续添加，不会另建歌单。"
                    else "未能确认创建结果，请先检查登录状态和音乐库，再决定是否重试。") }
            } finally {
                _state.update { it.copy(saving = false, activePlaylistId = writer.playlistId ?: it.activePlaylistId) }
            }
        }
    }
}
