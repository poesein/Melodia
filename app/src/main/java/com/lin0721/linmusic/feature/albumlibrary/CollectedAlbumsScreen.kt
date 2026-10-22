package com.lin0721.linmusic.feature.albumlibrary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import com.lin0721.linmusic.feature.library.ui.LibraryGridItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Independent host; the upstream library keeps its filters, card renderer and data model. */
@Composable
fun CollectedAlbumsScreen(
    onAlbumClick: (Long) -> Unit,
    onLoginScreenVisibilityChanged: (Boolean) -> Unit,
    isActive: Boolean = true,
    viewModel: CollectedAlbumsViewModel = koinViewModel(),
    layoutModel: CollectedAlbumsLayoutViewModel = koinViewModel(),
    syncProfile: SyncProfileAfterLoginUseCase = koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preferences by layoutModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(preferences.layout.sort, state.loading, state.albums) {
        if (preferences.layout.sort in setOf(AlbumPageSort.RELEASE_NEWEST, AlbumPageSort.RELEASE_OLDEST)) viewModel.ensureReleaseDates()
    }
    var login by remember { mutableStateOf(false) }
    var webLogin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(webLogin) { onLoginScreenVisibilityChanged(webLogin) }
    // AnimatedContent retains an outgoing root briefly. Cancel as soon as it stops
    // being the destination, rather than allowing a slow old request to replace
    // playback the user starts on another page during that transition.
    LaunchedEffect(isActive, viewModel) { if (!isActive) viewModel.cancelPlaybackPreparation() }
    DisposableEffect(viewModel) { onDispose {
        viewModel.cancelPlaybackPreparation()
        onLoginScreenVisibilityChanged(false)
    } }
    LaunchedEffect(viewModel) {
        viewModel.refresh()
        viewModel.events.collect { ToastManager.showToast(it) }
    }
    fun finishLogin(cookies: String) {
        login = false; webLogin = false
        scope.launch {
            try {
                if (syncProfile(cookies) == null) ToastManager.showToast("登录信息同步失败，请重试")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ToastManager.showToast("登录信息同步失败，请重试") }
        }
    }
    CollectedAlbumsPage(
        state = state, layout = preferences.layout, layoutReady = preferences.loaded,
        layoutError = preferences.error, onAlbumClick = { id ->
            if (isActive) { viewModel.cancelPlaybackPreparation(); onAlbumClick(id) }
        },
        onPlay = { albums, random -> if (isActive) viewModel.play(albums, random) },
        onCancelPreparation = viewModel::cancelPlaybackPreparation,
        onRetry = { if (!preferences.loaded) layoutModel.retry() else viewModel.refresh() },
        onLogin = { login = true }
    )
    if (login) LoginBottomSheet(onDismiss = { login = false }, onWebLogin = { login = false; webLogin = true }, onLoginSuccess = ::finishLogin)
    if (webLogin) WebViewLoginScreen(onClose = { webLogin = false }, onLoginSuccess = ::finishLogin)
}

/** One bounded lazy grid owns vertical scrolling. No wrapping sheet, scrollable parent or measured-height feedback. */
@Composable
internal fun CollectedAlbumsPage(
    state: CollectedAlbumsState,
    layout: AlbumPageLayout,
    onAlbumClick: (Long) -> Unit,
    onPlay: (List<AlbumSubItem>, Boolean) -> Unit,
    onCancelPreparation: () -> Unit,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    layoutReady: Boolean = true,
    layoutError: String? = null,
    gridState: LazyGridState = rememberLazyGridState()
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(state.albums, query, layout) { displayedAlbums(state.albums, query, layout) }
    val canPlay = state.loggedIn && layoutReady && !state.loading && !state.loadingReleaseDates && !state.preparing && shown.isNotEmpty()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("专辑", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1)
                MelodiaIconButton(onClick = { onPlay(shown, false) }, enabled = canPlay) { Icon(Icons.Rounded.PlayArrow, "全部播放专辑") }
                MelodiaIconButton(onClick = { onPlay(shown, true) }, enabled = canPlay) { Icon(Icons.Rounded.Shuffle, "随机播放专辑（专辑内顺序）") }
                MelodiaIconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Rounded.Search, "搜索收藏专辑") }
            }
            if (state.loadingReleaseDates) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (searching) OutlinedTextField(query, { query = it }, singleLine = true,
                placeholder = { Text("搜索专辑或歌手") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                trailingIcon = { MelodiaIconButton(onClick = { query = ""; searching = false }) { Icon(Icons.Rounded.Close, "关闭专辑搜索") } })
            if (state.preparing) {
                LinearProgressIndicator(progress = { if (state.totalAlbums > 0) state.preparedAlbums.toFloat() / state.totalAlbums else 0f }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("正在准备专辑 ${state.preparedAlbums}/${state.totalAlbums}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancelPreparation) { Text("取消") }
                }
            }
            LazyVerticalGrid(GridCells.Fixed(layout.normalized().columns), state = gridState,
                modifier = Modifier.weight(1f).testTag("collected_albums_grid"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = LocalBottomOverlayInset.current + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    !state.loggedIn -> item(span = { GridItemSpan(maxLineSpan) }, key = "login") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("登录后查看收藏的专辑")
                            TextButton(onClick = onLogin) { Text("登录") }
                        }
                    }
                    !layoutReady || state.loading && state.albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                        if (layoutError != null) Column { Text(layoutError); TextButton(onClick = onRetry) { Text("重试") } }
                        else Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    else -> {
                        if (state.error != null) item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                            Column { Text(state.error, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRetry) { Text("重试") } }
                        }
                        if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                            Text(if (query.isBlank()) "还没有收藏专辑" else "没有找到匹配的专辑", Modifier.padding(vertical = 40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else if (layout.groupByArtist) {
                            shown.groupBy { it.artistLabel() }.forEach { (artist, albums) ->
                                item(span = { GridItemSpan(maxLineSpan) }, key = "artist:$artist") {
                                    Text("$artist · ${albums.size}", modifier = Modifier.padding(top = 8.dp), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                items(albums, key = { "album:${it.id}" }) { album -> LibraryGridItem(album.libraryItem(), onClick = { onAlbumClick(album.id) }) }
                            }
                        } else items(shown, key = { "album:${it.id}" }) { album -> LibraryGridItem(album.libraryItem(), onClick = { onAlbumClick(album.id) }) }
                    }
                }
            }
        }
    }
}
