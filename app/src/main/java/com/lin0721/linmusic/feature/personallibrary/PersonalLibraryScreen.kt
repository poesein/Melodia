package com.lin0721.linmusic.feature.personallibrary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.feature.library.textimport.PlaylistTextImportSheet
import com.lin0721.linmusic.feature.library.ui.LibraryCreatePlaylistSheet
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.library.ui.LibraryUiState
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import org.koin.androidx.compose.koinViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicNavigation

/** A separate host/view-model key keeps the original library's filters and screen untouched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalLibraryScreen(onPlaylistClick: (Long) -> Unit, onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit, onLocalMusic: () -> Unit, onSearch: () -> Unit, onRecognition: () -> Unit,
    localNavigation: LocalMusicNavigation,
    onLoginScreenVisibilityChanged: (Boolean) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(key = "personal-library-data"),
    layoutModel: LibraryPageLayoutViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val layouts by layoutModel.state.collectAsStateWithLifecycle()
    val layoutState = layouts.getValue(LibraryPage.PERSONAL)
    val displayedLayout = layoutState.layout.withLocalShelfSettings(layouts.getValue(LibraryPage.LOCAL).layout)
    var settings by rememberSaveable { mutableStateOf(false) }
    var managedSection by rememberSaveable { mutableStateOf<String?>(null) }
    val onlineVisible = layoutState.layout.normalized(LibraryPage.PERSONAL).sections.any { it.visible && !it.key.startsWith("local_") }
    val local = if (settings || managedSection?.startsWith("local_") == true || layoutState.layout.normalized(LibraryPage.PERSONAL).sections.any { it.visible && it.key.startsWith("local_") }) {
        rememberPersonalLocalLibrary(localNavigation)
    } else null
    var createMenu by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf(false) }
    var webLogin by remember { mutableStateOf(false) }
    var collection by rememberSaveable { mutableStateOf<LibraryItemType?>(null) }
    LaunchedEffect(webLogin) { onLoginScreenVisibilityChanged(webLogin) }
    DisposableEffect(Unit) { onDispose { onLoginScreenVisibilityChanged(false) } }
    LaunchedEffect(viewModel) {
        // Refresh retained data after returning from collection/detail screens, as the original library does.
        // First entry is already loaded by LibraryViewModel's profile collector.
        if (viewModel.uiState.value is LibraryUiState.Success) viewModel.loadLibraryData()
        viewModel.toastEvent.collect { com.lin0721.linmusic.core.ui.components.ToastManager.showToast(it) }
    }
    fun open(item: LibraryItem) {
        val id = item.id.toLongOrNull() ?: return
        when (item.type) {
            LibraryItemType.PLAYLIST -> onPlaylistClick(id)
            LibraryItemType.ARTIST -> onArtistClick(id)
            LibraryItemType.ALBUM -> onAlbumClick(id)
        }
    }
    val items = (uiState as? LibraryUiState.Success)?.filteredItems.orEmpty()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground) {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                PersonalLibraryToolbar({ settings = true }, onLocalMusic, onSearch, onRecognition, { createMenu = true })
                Box(Modifier.padding(end = 40.dp).width(56.dp).height(56.dp)) {
                  DropdownMenu(expanded = createMenu, onDismissRequest = { createMenu = false }) {
                    DropdownMenuItem(text = { Text("新建歌单") }, onClick = {
                        createMenu = false; if (profile == null) login = true else create = true
                    })
                    DropdownMenuItem(text = { Text("导入歌单（TXT / 文本）") }, onClick = {
                        createMenu = false; if (profile == null) login = true else importing = true
                    })
                  }
                }
            }
            PersonalLibraryContent(items, displayedLayout, ::open, { collection = it },
                local = local?.data, localActions = local?.actions ?: PersonalLocalActions(), onlineStatus = {
              when {
                !onlineVisible -> Unit
                profile == null -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("登录后查看网易云歌单、歌手和专辑")
                    TextButton(onClick = { login = true }) { Text("登录") }
                }
                uiState is LibraryUiState.Loading -> Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                uiState is LibraryUiState.Error -> Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text((uiState as LibraryUiState.Error).message)
                    TextButton(onClick = { viewModel.loadLibraryData() }) { Text("重试") }
                }
                else -> Unit
              }
            })
        }
        if (settings) ModalBottomSheet(onDismissRequest = { settings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            LazyColumn(contentPadding = PaddingValues(20.dp), modifier = Modifier.navigationBarsPadding()) {
                item { Text("个人音乐库样式", style = MaterialTheme.typography.titleLarge) }
                item { LibraryPageLayoutEditor(LibraryPage.PERSONAL, layoutState.copy(layout = displayedLayout), onManageSection = { managedSection = it; settings = false }) { layoutModel.save(LibraryPage.PERSONAL, it) } }
                item { TextButton(onClick = { settings = false }, modifier = Modifier.fillMaxWidth()) { Text("完成") } }
            }
        }
        managedSection?.let { section ->
            val targetPage = if (section.startsWith("local_")) LibraryPage.LOCAL else LibraryPage.PERSONAL
            val targetKey = if (targetPage == LibraryPage.LOCAL) localSectionKey(section) else section
            LibrarySectionItemsSheet(targetPage, targetKey, layouts.getValue(targetPage),
                personalLayoutCatalog(items, local?.data)[section].orEmpty(),
                onSave = { layoutModel.save(targetPage, it) }, onDismiss = { managedSection = null; settings = true })
        }
        collection?.let { type -> PersonalLibraryCollectionSheet(type, items.filter { it.type == type }, layoutState.layout, ::open, { collection = null }) }
        if (create) LibraryCreatePlaylistSheet(onDismiss = { create = false }, onCreate = { viewModel.createPlaylist(it); create = false })
        if (importing) PlaylistTextImportSheet(onDismiss = { importing = false }, onImported = {
            importing = false; viewModel.loadLibraryData(); onPlaylistClick(it)
        })
        if (login) LoginBottomSheet(onDismiss = { login = false }, onWebLogin = { login = false; webLogin = true },
            onLoginSuccess = { login = false; viewModel.handleLoginSuccess(it) })
        if (webLogin) WebViewLoginScreen(onClose = { webLogin = false }, onLoginSuccess = { webLogin = false; viewModel.handleLoginSuccess(it) })
      }
    }
}
