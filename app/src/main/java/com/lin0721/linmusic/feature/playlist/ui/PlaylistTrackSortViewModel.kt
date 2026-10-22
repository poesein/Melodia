package com.lin0721.linmusic.feature.playlist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class PlaylistTrackSortState(
    val target: PlaylistSortTarget? = null,
    val scope: PlaylistSortScope? = null,
    val sort: PlaylistTrackSort = PlaylistTrackSort(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

/** Owns durable display order separately from the shared playlist loader and playback controller. */
class PlaylistTrackSortViewModel(
    private val repository: PlaylistTrackSortRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlaylistTrackSortState())
    val state = mutableState.asStateFlow()
    private var target: PlaylistSortTarget? = null
    private var observation: Job? = null

    fun bind(target: PlaylistSortTarget) {
        if (this.target == target && observation != null) return
        this.target = target
        observe(target)
    }

    fun retry() { target?.let(::observe) }

    private fun observe(target: PlaylistSortTarget) {
        observation?.cancel()
        mutableState.value = PlaylistTrackSortState(target = target)
        observation = viewModelScope.launch {
            try {
                // Unlike the playlist VM's initial null StateFlow, this first emission is the
                // actual persisted account (including a genuinely logged-out guest).
                userPreferences.userProfile.map { it?.uid?.takeIf { uid -> uid > 0 } }
                    .distinctUntilChanged().collectLatest { accountId ->
                        val scope = PlaylistSortScope(accountId, target)
                        mutableState.value = PlaylistTrackSortState(target = target, scope = scope)
                        try {
                            // Multiple retained NavFrames may own a VM for this same scope.
                            // Continue observing accepted changes instead of reading only once.
                            repository.observeState(scope).collect { saved ->
                                mutableState.value = PlaylistTrackSortState(target, scope,
                                    saved.sort, loaded = saved.loaded, saving = saved.saving, error = saved.error)
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            mutableState.value = PlaylistTrackSortState(target, scope,
                                error = error.message ?: "排序设置读取失败，请重试")
                        }
                    }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.value = PlaylistTrackSortState(target = target,
                    error = error.message ?: "账户设置读取失败，请重试")
            }
        }
    }

    fun save(scope: PlaylistSortScope, sort: PlaylistTrackSort) {
        val current = state.value
        if (!current.loaded || current.scope != scope) return
        try {
            // Submission is synchronous; persistence is owned by the repository's application
            // scope and keeps running if this frame is popped immediately after the tap.
            repository.submitSave(scope, sort)
            mutableState.value = current.copy(sort = sort, saving = true, error = null)
        } catch (error: Exception) {
            mutableState.value = current.copy(error = "排序保存失败，请重新选择排序：${error.message.orEmpty()}")
        }
    }

    fun reset(scope: PlaylistSortScope) {
        if (state.value.scope != scope || state.value.saving) return
        try {
            repository.submitReset(scope)
            mutableState.value = state.value.copy(sort = PlaylistTrackSort(), loaded = true, saving = true, error = null)
            // A previous disk I/O failure may have ended that scope's collection.
            retry()
        } catch (error: Exception) {
            mutableState.value = state.value.copy(saving = false, error = error.message ?: "排序设置重置失败，请重试")
        }
    }
}
