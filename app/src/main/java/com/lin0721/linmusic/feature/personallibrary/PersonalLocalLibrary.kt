package com.lin0721.linmusic.feature.personallibrary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.components.CreatePlaylistDialog
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.feature.localmusic.domain.*
import com.lin0721.linmusic.feature.localmusic.ui.*
import com.lin0721.linmusic.feature.localmusic.ui.components.*
import com.lin0721.linmusic.feature.localmusic.ui.playlist.LocalPlaylistCover
import com.lin0721.linmusic.feature.localmusic.ui.playlist.NewPlaylistTile
import org.koin.androidx.compose.koinViewModel

/** Local shelves share the existing library index and actions; nothing is copied to online records. */
internal data class PersonalLocalLibrary(
    val library: LocalLibraryIndex = LocalLibraryIndex.EMPTY,
    val playlists: List<LocalPlaylist> = emptyList(),
    val playingMediaId: String? = null,
    val isPlaying: Boolean = false,
    val status: String? = null,
    val needsPermission: Boolean = false,
    val loading: Boolean = false
)

internal data class PersonalLocalActions(
    val onMore: (String) -> Unit = {},
    val onTrack: (LocalTrack, List<LocalTrack>) -> Unit = { _, _ -> },
    val onTrackMenu: (LocalTrack, List<LocalTrack>, String?) -> Unit = { _, _, _ -> },
    val onArtist: (String) -> Unit = {}, val onAlbum: (String) -> Unit = {},
    val onPlaylist: (Long) -> Unit = {}, val onCreatePlaylist: () -> Unit = {},
    val onRetry: () -> Unit = {}
)

internal data class PersonalLocalBinding(val data: PersonalLocalLibrary, val actions: PersonalLocalActions)

/** Only composed when at least one local shelf is enabled. Permission is requested on an explicit tap. */
@Composable
internal fun rememberPersonalLocalLibrary(navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel = koinViewModel()): PersonalLocalBinding {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playingId by viewModel.playingMediaId.collectAsStateWithLifecycle()
    val playing by viewModel.isPlaying.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.checkPermissionAndLoad() }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.ensureLoaded() }
    LocalTrackActionsHost(viewModel, navigation)
    if (creating) CreatePlaylistDialog(onDismiss = { creating = false }, onCreate = { viewModel.createPlaylist(it) },
        title = "新建本地歌单", confirmText = "创建")
    val message = when (val current = state) {
        is LocalMusicUiState.Loading -> "正在加载本地曲库…"
        is LocalMusicUiState.NeedsPermission -> "需要访问设备音频权限才能显示本地栏目"
        is LocalMusicUiState.Error -> current.message
        is LocalMusicUiState.Success -> null
    }
    return PersonalLocalBinding(PersonalLocalLibrary(
        library = if (state is LocalMusicUiState.Success) library else LocalLibraryIndex.EMPTY,
        playlists = if (state is LocalMusicUiState.Success) playlists else emptyList(),
        playingMediaId = playingId, isPlaying = playing, status = message,
        needsPermission = state is LocalMusicUiState.NeedsPermission, loading = state is LocalMusicUiState.Loading), PersonalLocalActions(
        onMore = { key -> when (key) {
            "local_recent" -> navigation.openSongs()
            "local_playlists" -> navigation.openPlaylists()
            "local_artists" -> navigation.openArtists()
            "local_albums" -> navigation.openAlbums()
        } },
        onTrack = { track, tracks -> viewModel.playTracks(tracks, start = track) },
        onTrackMenu = { track, tracks, cover -> viewModel.openTrackMenu(track, tracks, cover) },
        onArtist = navigation.openArtist, onAlbum = navigation.openAlbum,
        onPlaylist = navigation.openPlaylist, onCreatePlaylist = { creating = true },
        onRetry = {
            val current = state
            if (current is LocalMusicUiState.NeedsPermission) launcher.launch(current.permission)
            else viewModel.checkPermissionAndLoad()
        }
    ))
}

/** Adds items to the same vertical list, so local and online sections reorder as peers. */
internal fun LazyListScope.personalLocalSection(key: String, data: PersonalLocalLibrary, actions: PersonalLocalActions,
    recentTracks: List<LocalTrack>, layout: LibraryPageLayout) {
    val title = LibraryPage.PERSONAL.sections.first { it.first == key }.second
    item("${key}_header") { LocalSectionHeader(title, onMoreClick = { actions.onMore(key) }) }
    val library = data.library
    when (key) {
        "local_recent" -> {
            val tracks = recentTracks
            if (tracks.isEmpty()) localEmpty(key)
            items(tracks.take(5), key = { "personal_recent_${it.uri}" }) { track ->
                LocalTrackRow(track = track, subtitle = track.artist, playingMediaId = data.playingMediaId,
                    isPlaying = data.isPlaying, onClick = { actions.onTrack(track, tracks) },
                    onMoreClick = { actions.onTrackMenu(track, tracks, it) })
            }
        }
        "local_playlists" -> item("${key}_shelf") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item("personal_new_local_playlist") {
                    Column(Modifier.width(108.dp).pressable(MelodiaPress.Card, onClick = actions.onCreatePlaylist)) {
                        NewPlaylistTile(size = 108.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("新建本地歌单", fontSize = 13.sp)
                    }
                }
                items(layout.shelfItems(key, data.playlists) { it.id.toString() }, key = { it.id }) { playlist ->
                    Column(Modifier.width(108.dp).pressable(MelodiaPress.Card) { actions.onPlaylist(playlist.id) }) {
                        LocalPlaylistCover(playlist.tracks, 108.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(playlist.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${playlist.tracks.size} 首", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        "local_artists" -> {
            if (library.artists.isEmpty()) localEmpty(key)
            else item("${key}_shelf") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(layout.shelfItems(key, library.artists) { it.name }, key = { it.name }) { artist ->
                        Column(Modifier.width(64.dp).pressable(MelodiaPress.Card) { actions.onArtist(artist.name) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            LocalCover(artist.tracks.first().uri, size = 64.dp, shape = CircleShape, fallbackIcon = Icons.Rounded.Person)
                            Spacer(Modifier.height(6.dp))
                            Text(artist.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        "local_albums" -> {
            if (library.knownAlbums.isEmpty()) localEmpty(key)
            else item("${key}_shelf") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(layout.shelfItems(key, library.recentAlbums) { it.key }, key = { it.key }) { album ->
                        LocalAlbumCard(coverSourceUri = album.coverSourceUri, title = album.name, subtitle = album.artist,
                            width = 108.dp, onClick = { actions.onAlbum(album.key) })
                    }
                }
            }
        }
    }
}

private fun LazyListScope.localEmpty(key: String) {
    item("${key}_empty") { Text("暂无内容", Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
