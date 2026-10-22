package com.lin0721.linmusic.feature.personallibrary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.scroll.rememberExpandedSheetScrollBoundary
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonalPlaylistSelectionSheet(items: List<LibraryItem>, state: LibraryPageLayoutState,
    onSave: ((LibraryPageLayout) -> LibraryPageLayout) -> Unit, onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    listState: LazyListState = rememberLazyListState()) {
    val boundary = rememberExpandedSheetScrollBoundary(sheetState) { listState.canScrollForward }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        PersonalPlaylistSelectionContent(items, state, onSave, onDismiss, listState,
            Modifier.nestedScroll(boundary))
    }
}

@Composable
internal fun PersonalPlaylistSelectionContent(items: List<LibraryItem>, state: LibraryPageLayoutState,
    onSave: ((LibraryPageLayout) -> LibraryPageLayout) -> Unit, onDismiss: () -> Unit,
    listState: LazyListState = rememberLazyListState(), modifier: Modifier = Modifier) {
    val playlists = remember(items) { items.filter { it.type == LibraryItemType.PLAYLIST }.distinctBy { it.id } }
    val ids = remember(playlists) { playlists.mapTo(linkedSetOf()) { it.id } }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(playlists, query) { playlists.filter { query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true) } }
    val enabled = state.loaded && !state.saving
    Column(modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("选择显示的歌单", style = MaterialTheme.typography.titleLarge)
        Text("已选 ${playlists.count { state.layout.playlistIsVisible(it.id) }} / ${playlists.size} · 点击直接保存", style = MaterialTheme.typography.bodyMedium)
        Text("仅调整个人音乐库的显示；新建歌单（含文本导入新建）默认显示。", style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(enabled = enabled && ids.isNotEmpty(), onClick = { onSave { it.copy(visiblePlaylistIds = it.visiblePlaylistIds.orEmpty() + ids) } }) { Text("全选") }
            TextButton(enabled = enabled && ids.isNotEmpty(), onClick = { onSave { it.copy(visiblePlaylistIds = (it.visiblePlaylistIds ?: ids) - ids) } }) { Text("清除选择") }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("查找歌单") }, modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("playlist_visibility_list")) {
            items(shown, key = { it.id }) { item ->
                val checked = state.layout.playlistIsVisible(item.id)
                fun toggle(show: Boolean) { onSave { it.setPlaylistVisible(item.id, show, ids) } }
                Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { toggle(!checked) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LibraryRemoteCover(item, Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Switch(checked = checked, enabled = enabled, onCheckedChange = ::toggle,
                        modifier = Modifier.semantics { contentDescription = "显示歌单：${item.title}" })
                }
            }
            if (shown.isEmpty()) item { Text(if (playlists.isEmpty()) "暂无歌单，请登录并等待收藏加载" else "未找到歌单", Modifier.padding(vertical = 16.dp)) }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
    }
}
