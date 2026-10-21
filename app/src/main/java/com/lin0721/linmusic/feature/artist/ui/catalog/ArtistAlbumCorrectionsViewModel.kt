package com.lin0721.linmusic.feature.artist.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistAlbumCorrectionsRepository
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistAlbumCorrections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistAlbumCorrectionsState(
    val artistId: Long = 0,
    val value: ArtistAlbumCorrections = ArtistAlbumCorrections(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

class ArtistAlbumCorrectionsViewModel(private val repository: ArtistAlbumCorrectionsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ArtistAlbumCorrectionsState())
    val state = mutableState.asStateFlow()
    private var observer: Job? = null
    private var generation = 0L

    fun selectArtist(artistId: Long, retry: Boolean = false) {
        if (artistId <= 0 || (state.value.artistId == artistId && !retry)) return
        val request = ++generation
        observer?.cancel()
        mutableState.value = ArtistAlbumCorrectionsState(artistId = artistId)
        observer = viewModelScope.launch {
            repository.observe(artistId).catch { error ->
                if (generation == request) mutableState.update { it.copy(error = error.message ?: "读取手动记录失败") }
            }.collect { value ->
                if (generation == request) mutableState.update { it.copy(value = value, loaded = true, error = null) }
            }
        }
    }

    fun retry() = selectArtist(state.value.artistId, retry = true)
    fun setEnabled(enabled: Boolean) = save { repository.setEnabled(it, enabled) }
    fun setDecision(album: ArtistAlbum, show: Boolean) = save { repository.setDecision(it, album, show) }
    fun setEntryActive(albumId: Long, active: Boolean) = save { repository.setEntryActive(it, albumId, active) }

    private fun save(change: suspend (Long) -> Unit) {
        val previous = state.value
        if (!previous.loaded || previous.saving) return
        val request = generation
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { change(previous.artistId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation == request) mutableState.update { it.copy(error = error.message ?: "保存失败，请重试") }
            } finally {
                if (generation == request) mutableState.update { it.copy(saving = false) }
            }
        }
    }
}
