package com.lin0721.linmusic.feature.personallibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryPageLayoutState(val layout: LibraryPageLayout, val loaded: Boolean = false,
    val saving: Boolean = false, val error: String? = null)

class LibraryPageLayoutViewModel(private val repository: LibraryPageLayoutRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryPage.entries.associateWith { LibraryPageLayoutState(LibraryPageLayout().normalized(it)) })
    val state = mutableState.asStateFlow()
    init {
        LibraryPage.entries.forEach { page -> viewModelScope.launch {
            repository.observe(page).catch { error -> update(page) { it.copy(error = error.message ?: "读取布局失败") } }
                .collect { layout -> update(page) { it.copy(layout = layout, loaded = true) } }
        } }
    }
    private fun update(page: LibraryPage, change: (LibraryPageLayoutState) -> LibraryPageLayoutState) {
        mutableState.update { values -> values + (page to change(values.getValue(page))) }
    }
    fun save(page: LibraryPage, change: (LibraryPageLayout) -> LibraryPageLayout) {
        val current = state.value.getValue(page)
        if (!current.loaded || current.saving) return
        update(page) { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { repository.update(page, change) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { update(page) { it.copy(error = error.message ?: "布局保存失败，请重试") } }
            finally { update(page) { it.copy(saving = false) } }
        }
    }
}
