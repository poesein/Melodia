package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.feature.settings.ui.SettingsRow
import org.koin.androidx.compose.koinViewModel

/** The same manager is hosted from settings and the two album-detail shortcuts. */
@Composable
fun LocalAlbumAssociationHost(viewModel: LocalAlbumAssociationViewModel) {
    val current by viewModel.state.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val hub by viewModel.hub.collectAsStateWithLifecycle()
    var showSongs by remember { mutableStateOf(false) }
    if (showSongs) {
        BatchLocalMatchManager(onDismiss = { showSongs = false })
    } else hub?.let { state ->
        LocalMatchingHubDialog(state, onDismiss = viewModel::close,
            onQueryChange = viewModel::hubQuery, onOnlineModeChange = viewModel::hubMode,
            onSearch = viewModel::hubSearch, onChooseLocal = viewModel::hubChooseLocal,
            onChooseOnline = viewModel::hubChooseOnline, onSongs = { showSongs = true })
    }
    picker?.let { state ->
        OnlineAlbumLocalPickerDialog(state, onDismiss = viewModel::close,
            onQueryChange = viewModel::pickerQuery, onShowAllChange = viewModel::pickerShowAll,
            onSelect = { viewModel.chooseLocal(it, state.online.id) })
    }
    current?.let { state ->
        LocalAlbumAssociationDialog(state, onDismiss = viewModel::close,
            onQueryChange = viewModel::query, onSearch = viewModel::searchAlbums,
            onSelectAlbum = viewModel::chooseAlbum, onChangeAlbum = viewModel::changeAlbum,
            onRefresh = viewModel::refresh, onApply = viewModel::apply, onStop = viewModel::stop,
            onLink = { local, online -> viewModel.link(local, online, state.selected?.id ?: 0L) },
            onForget = viewModel::forget)
    }
}

@Composable
fun LocalMatchingSettingsEntry(viewModel: LocalAlbumAssociationViewModel = koinViewModel()) {
    SettingsRow(title = "本地与网易云匹配", subtitle = "歌曲批量匹配、双向专辑对应与专辑内宽松匹配",
        onClick = viewModel::openHub)
    LocalAlbumAssociationHost(viewModel)
}
