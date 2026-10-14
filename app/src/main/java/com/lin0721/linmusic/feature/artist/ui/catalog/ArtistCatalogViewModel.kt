package com.lin0721.linmusic.feature.artist.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogRepository
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogArtist
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogMatcher
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogReleaseGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistCatalogUiState(
    val artistId: Long = 0L,
    val enabled: Boolean = false,
    val artistMbid: String? = null,
    val artistName: String? = null,
    val query: String = "",
    /** Null means no usable catalogue; an empty successful catalogue is distinct. */
    val groups: List<ArtistCatalogReleaseGroup>? = null,
    val candidates: List<ArtistCatalogArtist> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val searched: Boolean = false,
    val choosingArtist: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

/** Android-only catalogue state leaves the upstream artist loading and pagination unchanged. */
class ArtistCatalogViewModel(private val repository: ArtistCatalogRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ArtistCatalogUiState())
    val state = mutableState.asStateFlow()
    private var artist: ArtistDetailInfo? = null
    private var sourceAlbums: List<ArtistAlbum> = emptyList()
    private var preferenceJob: Job? = null
    private var requestJob: Job? = null
    private var proposedBinding: Pair<ArtistCatalogArtist, List<ArtistCatalogReleaseGroup>>? = null

    fun updateArtistContext(source: ArtistDetailInfo, albums: List<ArtistAlbum>) {
        if (source.id <= 0L) return
        sourceAlbums = albums
        if (artist?.id == source.id) {
            artist = source
            val proposal = proposedBinding
            if (proposal != null && state.value.enabled && state.value.artistMbid == null &&
                !state.value.loading && !state.value.saving &&
                ArtistCatalogMatcher.identityEvidence(albums, proposal.second,
                    boundArtistMbid = proposal.first.id).canAutoBind) bind(proposal.first)
            return
        }
        artist = source
        proposedBinding = null
        preferenceJob?.cancel()
        requestJob?.cancel()
        mutableState.value = ArtistCatalogUiState(artistId = source.id, query = source.name)
        preferenceJob = viewModelScope.launch {
            repository.selection(source.id).catch { error ->
                mutableState.update { it.copy(saving = false, loading = false,
                    error = "无法读取此歌手的资料库设置：${userError(error)}") }
            }.collect { preference ->
                if (artist?.id != source.id) return@collect
                val previous = state.value
                val changedBinding = previous.artistMbid != preference.artistMbid
                mutableState.update { it.copy(enabled = preference.enabled,
                    artistMbid = preference.artistMbid, artistName = preference.artistName,
                    groups = if (changedBinding) null else it.groups,
                    saving = false) }
                if (!preference.enabled) {
                    requestJob?.cancel()
                    mutableState.update { it.copy(loading = false, choosingArtist = false) }
                } else if (!previous.enabled || changedBinding) {
                    preference.artistMbid?.let { loadCatalogue(it, force = false) } ?: searchArtists()
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val id = artist?.id ?: return
        if (state.value.saving) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { repository.setEnabled(id, enabled) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(saving = false, error = userError(error)) } }
        }
    }

    fun updateQuery(query: String) {
        if (state.value.saving) return
        requestJob?.cancel()
        proposedBinding = null
        mutableState.update { it.copy(query = query, loading = false, searched = false,
            candidates = emptyList(), error = null, message = null) }
    }

    fun changeBinding() {
        if (!state.value.enabled || state.value.saving) return
        mutableState.update { it.copy(query = artist?.name.orEmpty(), choosingArtist = true) }
        searchArtists()
    }

    fun searchArtists() {
        val source = artist ?: return
        val query = state.value.query.trim()
        if (!state.value.enabled || state.value.saving || query.isBlank()) return
        requestJob?.cancel()
        mutableState.update { it.copy(loading = true, searched = true, choosingArtist = true,
            candidates = emptyList(), error = null, message = null) }
        requestJob = viewModelScope.launch {
            try {
                val candidates = repository.findArtists(query).getOrThrow()
                if (artist?.id != source.id || !state.value.enabled) return@launch
                mutableState.update { it.copy(candidates = candidates) }
                if (state.value.artistMbid == null) {
                    val names = listOfNotNull(source.name, source.trans) + source.alias.orEmpty()
                    val exact = ArtistCatalogMatcher.exactNameCandidates(candidates, names)
                    if (exact.size == 1) {
                        val candidate = exact.single()
                        val groups = repository.albums(candidate.id).getOrThrow()
                        proposedBinding = candidate to groups
                        if (ArtistCatalogMatcher.identityEvidence(sourceAlbums, groups,
                                boundArtistMbid = candidate.id).canAutoBind) {
                            repository.bindArtist(source.id, candidate.id, candidate.name)
                            mutableState.update { it.copy(loading = false, choosingArtist = false,
                                message = "已根据歌手名称与至少两张专辑确认资料库歌手") }
                            return@launch
                        }
                    }
                }
                mutableState.update { it.copy(loading = false,
                    message = if (candidates.isNotEmpty()) "请核对名称、别名和国别，点选歌手即可绑定。" else null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.update { it.copy(loading = false, error = userError(error)) }
            }
        }
    }

    fun bind(candidate: ArtistCatalogArtist) {
        val source = artist ?: return
        if (!state.value.enabled || state.value.saving) return
        requestJob?.cancel()
        mutableState.update { it.copy(saving = true, loading = false, error = null) }
        viewModelScope.launch {
            try {
                repository.bindArtist(source.id, candidate.id, candidate.name)
                proposedBinding = null
                mutableState.update { it.copy(saving = false, choosingArtist = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(saving = false, error = userError(error)) } }
        }
    }

    fun retry() {
        if (!state.value.enabled || state.value.saving) return
        state.value.artistMbid?.let { loadCatalogue(it, force = true) } ?: searchArtists()
    }

    private fun loadCatalogue(mbid: String, force: Boolean) {
        requestJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null, choosingArtist = false) }
        requestJob = viewModelScope.launch {
            try {
                repository.cachedAlbums(mbid)?.let { cached ->
                    if (state.value.artistMbid == mbid) mutableState.update { it.copy(groups = cached) }
                }
                val groups = repository.albums(mbid, force).getOrThrow()
                if (state.value.artistMbid == mbid && state.value.enabled) mutableState.update {
                    it.copy(groups = groups, loading = false, error = null)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (state.value.artistMbid == mbid) mutableState.update {
                    it.copy(loading = false, error = userError(error))
                }
            }
        }
    }

    private fun userError(error: Throwable): String = error.message?.takeIf(String::isNotBlank)?.take(180)
        ?: "音乐资料库暂时不可用，请稍后重试。"
}
