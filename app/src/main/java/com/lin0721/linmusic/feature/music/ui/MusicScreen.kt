package com.lin0721.linmusic.feature.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.feature.home.ui.FilterPillChip
import com.lin0721.linmusic.feature.newworks.ui.NewWorksFeedActions
import com.lin0721.linmusic.feature.newworks.ui.NewWorksFeedContent
import com.lin0721.linmusic.feature.newworks.ui.NewWorksViewModel
import org.koin.androidx.compose.koinViewModel

/** Music and new releases share a navigation root independent from the homepage. */
@Composable
fun MusicScreen(
    showNewWorksFeed: Boolean,
    onShowNewWorksFeedChanged: (Boolean) -> Unit,
    onPlaylistClick: (Long, Boolean) -> Unit,
    onStyleClick: (Long, String) -> Unit,
    onSearchClick: () -> Unit,
    musicViewModel: MusicViewModel = koinViewModel(),
    newWorksViewModel: NewWorksViewModel = koinViewModel()
) {
    val musicUiState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val newWorksUiState by newWorksViewModel.uiState.collectAsStateWithLifecycle()
    val collectState by newWorksViewModel.collectState.collectAsStateWithLifecycle()
    val importState by newWorksViewModel.importState.collectAsStateWithLifecycle()
    val status by newWorksViewModel.releaseStatus.collectAsStateWithLifecycle()

    LaunchedEffect(showNewWorksFeed, musicViewModel, newWorksViewModel) {
        if (showNewWorksFeed) newWorksViewModel.loadIfNeeded() else musicViewModel.loadIfNeeded()
    }
    LaunchedEffect(newWorksViewModel) {
        newWorksViewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterPillChip("音乐", selected = !showNewWorksFeed, onClick = { onShowNewWorksFeedChanged(false) })
            FilterPillChip("最新", selected = showNewWorksFeed, onClick = { onShowNewWorksFeedChanged(true) })
        }
        Box(Modifier.weight(1f)) {
            if (showNewWorksFeed) {
                NewWorksFeedContent(
                    uiState = newWorksUiState,
                    collectState = collectState,
                    importState = importState,
                    status = status,
                    actions = remember(newWorksViewModel, onPlaylistClick) {
                        NewWorksFeedActions(
                            onAlbumClick = { onPlaylistClick(it, true) },
                            onTogglePlay = newWorksViewModel::togglePlayRelease,
                            onToggleLibrary = newWorksViewModel::toggleInLibrary,
                            onAddToPlayNext = newWorksViewModel::addToPlayNext,
                            onPrepareCollect = newWorksViewModel::prepareCollectDialog,
                            onSaveCollection = newWorksViewModel::savePlaylistCollection,
                            onSaveNewCollection = newWorksViewModel::createPlaylistAndAddSong,
                            onPrepareImportTargets = newWorksViewModel::prepareImportTargets,
                            onAddToPlaylist = newWorksViewModel::addToPlaylist,
                            onCreatePlaylistAndAdd = newWorksViewModel::createPlaylistAndAdd,
                            onRetry = newWorksViewModel::load,
                            onLoadMore = newWorksViewModel::loadMore
                        )
                    }
                )
            } else {
                MusicContent(
                    uiState = musicUiState,
                    onStyleClick = onStyleClick,
                    onRetry = musicViewModel::loadStyles,
                    onSearchClick = onSearchClick
                )
            }
        }
    }
}
