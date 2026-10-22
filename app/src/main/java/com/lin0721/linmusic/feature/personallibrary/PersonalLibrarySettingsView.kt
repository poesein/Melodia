package com.lin0721.linmusic.feature.personallibrary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.feature.library.ui.LibraryUiState
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicUiState
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicViewModel
import com.lin0721.linmusic.feature.settings.ui.SettingsGroupCard
import com.lin0721.linmusic.feature.settings.ui.SettingsRow
import org.koin.androidx.compose.koinViewModel

/** Same DataStore/key as beta.23: moving the entry does not migrate or reset any saved choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalLibrarySettingsView(
    viewModel: LibraryViewModel = koinViewModel(key = "personal-library-data"),
    layoutModel: LibraryPageLayoutViewModel = koinViewModel(),
    localModel: LocalMusicViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val layouts by layoutModel.state.collectAsStateWithLifecycle()
    val library by localModel.library.collectAsStateWithLifecycle()
    val playlists by localModel.playlists.collectAsStateWithLifecycle()
    val localState by localModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localModel.checkPermissionAndLoad() }
    var managed by rememberSaveable { mutableStateOf<String?>(null) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val current = layouts.getValue(LibraryPage.PERSONAL)
    val displayed = current.layout.withLocalShelfSettings(layouts.getValue(LibraryPage.LOCAL).layout)
    val items = (state as? LibraryUiState.Success)?.allItems.orEmpty()
    LaunchedEffect(localModel) { localModel.ensureLoaded() }
    LaunchedEffect(viewModel) { if (viewModel.uiState.value is LibraryUiState.Success) viewModel.loadLibraryData() }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item("playlist_visibility") {
            SettingsGroupCard("显示歌单") {
                SettingsRow(title = "选择显示的歌单", subtitle = "按歌单选择显示或隐藏，包括听歌排行", onClick = { choosing = true })
                if (state is LibraryUiState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state is LibraryUiState.Error) TextButton(onClick = { viewModel.loadLibraryData() }) { Text("重新加载收藏") }
            }
        }
        item("layout") {
            SettingsGroupCard("布局与栏目") {
                LibraryPageLayoutEditor(LibraryPage.PERSONAL, current.copy(layout = displayed), onManageSection = { managed = it }) {
                    layoutModel.save(LibraryPage.PERSONAL, it)
                }
            }
        }
        if (localState is LocalMusicUiState.NeedsPermission) item("local_permission") {
            TextButton(onClick = { permission.launch((localState as LocalMusicUiState.NeedsPermission).permission) }) { Text("授权访问本地音频") }
        }
    }
    managed?.let { section ->
        val target = if (section.startsWith("local_")) LibraryPage.LOCAL else LibraryPage.PERSONAL
        val key = if (target == LibraryPage.LOCAL) localSectionKey(section) else section
        LibrarySectionItemsSheet(target, key, layouts.getValue(target),
            personalLayoutCatalog(items, PersonalLocalLibrary(library, playlists))[section].orEmpty(),
            onSave = { layoutModel.save(target, it) }, onDismiss = { managed = null })
    }
    if (choosing) PersonalPlaylistSelectionSheet(items, current,
        onSave = { layoutModel.save(LibraryPage.PERSONAL, it) }, onDismiss = { choosing = false })
}
