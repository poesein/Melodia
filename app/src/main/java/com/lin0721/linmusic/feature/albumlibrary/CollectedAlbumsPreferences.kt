package com.lin0721.linmusic.feature.albumlibrary

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Context.collectedAlbumsLayout by preferencesDataStore(name = "collected_albums_layout")

enum class AlbumPageSort { RECENTLY_COLLECTED, NAME }

data class AlbumPageLayout(
    val columns: Int = 3,
    val groupByArtist: Boolean = false,
    val sort: AlbumPageSort = AlbumPageSort.RECENTLY_COLLECTED
) {
    fun normalized() = copy(columns = columns.coerceIn(1, 5))
}

/** Display preferences belong to this page, never to scan, match or account storage. */
class CollectedAlbumsPreferences internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.collectedAlbumsLayout)
    private val columns = intPreferencesKey("columns")
    private val grouping = booleanPreferencesKey("group_by_artist")
    private val sort = stringPreferencesKey("sort")
    private fun decode(p: Preferences) = AlbumPageLayout(
        p[columns] ?: 3,
        p[grouping] ?: false,
        AlbumPageSort.entries.firstOrNull { it.name == p[sort] } ?: AlbumPageSort.RECENTLY_COLLECTED
    ).normalized()
    val layout = store.data.map(::decode).distinctUntilChanged()
    suspend fun update(change: (AlbumPageLayout) -> AlbumPageLayout) {
        store.edit { prefs ->
            val value = change(decode(prefs)).normalized()
            prefs[columns] = value.columns
            prefs[grouping] = value.groupByArtist
            prefs[sort] = value.sort.name
        }
    }
}

data class AlbumPageLayoutState(val layout: AlbumPageLayout = AlbumPageLayout(), val loaded: Boolean = false, val error: String? = null)

class CollectedAlbumsLayoutViewModel(private val preferences: CollectedAlbumsPreferences) : ViewModel() {
    private val mutableState = MutableStateFlow(AlbumPageLayoutState())
    val state = mutableState.asStateFlow()
    private var observer: Job? = null
    init { retry() }
    fun retry() {
        observer?.cancel()
        observer = viewModelScope.launch {
            preferences.layout.catch { error ->
                mutableState.update { it.copy(loaded = false, error = error.message ?: "读取专辑页设置失败") }
            }.collect { layout -> mutableState.value = AlbumPageLayoutState(layout, loaded = true) }
        }
    }
    fun save(change: (AlbumPageLayout) -> AlbumPageLayout) {
        if (!state.value.loaded) return
        viewModelScope.launch {
            try {
                preferences.update(change)
                mutableState.update { it.copy(error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "保存专辑页设置失败") }
            }
        }
    }
}
