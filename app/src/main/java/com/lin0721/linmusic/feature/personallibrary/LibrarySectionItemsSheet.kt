package com.lin0721.linmusic.feature.personallibrary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.localmusic.domain.LocalLibraryIndex
import com.lin0721.linmusic.feature.localmusic.domain.LocalPlaylist

internal data class LibrarySortItem(val id: String, val title: String, val subtitle: String = "")

internal fun localLayoutCatalog(library: LocalLibraryIndex, playlists: List<LocalPlaylist>, personal: Boolean = false): Map<String, List<LibrarySortItem>> = mapOf(
    "local_playlists" to playlists.map { LibrarySortItem(it.id.toString(), it.name, "${it.tracks.size} 首") },
    (if (personal) "local_artists" else "artists") to library.artists.map { LibrarySortItem(it.name, it.name, "${it.tracks.size} 首") },
    (if (personal) "local_albums" else "albums") to library.recentAlbums.map { LibrarySortItem(it.key, it.name, it.artist) }
)

internal fun personalLayoutCatalog(items: List<LibraryItem>, local: PersonalLocalLibrary?): Map<String, List<LibrarySortItem>> =
    listOf("playlists" to LibraryItemType.PLAYLIST, "artists" to LibraryItemType.ARTIST, "albums" to LibraryItemType.ALBUM)
        .associate { (key, type) -> key to items.filter { it.type == type }.map { LibrarySortItem(it.id, it.title, it.subtitle) } } +
        localLayoutCatalog(local?.library ?: LocalLibraryIndex.EMPTY, local?.playlists.orEmpty(), personal = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySectionItemsSheet(page: LibraryPage, section: String, state: LibraryPageLayoutState,
    items: List<LibrarySortItem>, onSave: ((LibraryPageLayout) -> LibraryPageLayout) -> Unit, onDismiss: () -> Unit) {
    val title = page.sections.first { it.first == section }.second
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LibrarySectionItemsContent(title, section, state, items, onSave, onDismiss)
    }
}

@Composable
internal fun LibrarySectionItemsContent(title: String, section: String, state: LibraryPageLayoutState,
    items: List<LibrarySortItem>, onSave: ((LibraryPageLayout) -> LibraryPageLayout) -> Unit, onDismiss: () -> Unit) {
    val enabled = state.loaded && !state.saving
    var query by rememberSaveable(section) { mutableStateOf("") }
    var limit by rememberSaveable(section, state.layout.itemLimit(section)) { mutableStateOf(state.layout.itemLimit(section).toString()) }
    val number = limit.toIntOrNull()?.takeIf { it in 0..5000 }
    val ordered = remember(items, state.layout.itemOrders[section]) { state.layout.orderedItems(section, items) { it.id } }
    val filtered = remember(ordered, query) { ordered.filter { query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true) } }
    val positions = remember(ordered) { ordered.withIndex().associate { it.value.id to it.index } }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$title · 显示与排序", style = MaterialTheme.typography.titleLarge)
        if (supportsItemLimit(section)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(limit, { if (it.length <= 4 && it.all(Char::isDigit)) limit = it },
                    label = { Text("横栏显示数量（0 为全部）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = number == null,
                    modifier = Modifier.weight(1f), enabled = enabled)
                TextButton(enabled = enabled && number != null && number != state.layout.itemLimit(section),
                    onClick = { number?.let { n -> onSave { it.copy(itemLimits = it.itemLimits + (section to n)) } } }) { Text("保存数量") }
            }
            Text("默认 10 项，可设 1–5000；0 显示全部。", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(query, { query = it }, label = { Text("查找要排序的条目") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("置顶、上移或下移直接保存；查找时仍按完整列表移动。新增条目排在已排序条目之后。", style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f).testTag("library_section_items")) {
            itemsIndexed(filtered, key = { _, item -> item.id }) { _, item ->
                val index = positions.getValue(item.id)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${index + 1}. ${item.title}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    fun move(direction: Int) { onSave { it.moveItem(section, item.id, direction, items.map { entry -> entry.id }) } }
                    IconButton(enabled = enabled && index > 0, onClick = { move(0) }) { Icon(Icons.Rounded.VerticalAlignTop, "置顶：${item.title}") }
                    IconButton(enabled = enabled && index > 0, onClick = { move(-1) }) { Icon(Icons.Rounded.ArrowUpward, "上移：${item.title}") }
                    IconButton(enabled = enabled && index < ordered.lastIndex, onClick = { move(1) }) { Icon(Icons.Rounded.ArrowDownward, "下移：${item.title}") }
                }
            }
            if (filtered.isEmpty()) item { Text(if (items.isEmpty()) "暂无条目；请确认收藏已加载，或本地曲库已获得授权" else "未找到条目", Modifier.padding(vertical = 16.dp)) }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
    }
}
