package com.lin0721.linmusic.feature.personallibrary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalSectionHeader

/** Shared preview policy: up to five actual rows, never reserve space for absent records. */
internal fun playlistPreviewRows(items: List<LibraryItem>, columns: Int): List<List<LibraryItem>> =
    items.take(5 * columns.coerceIn(1, 5)).chunked(columns.coerceIn(1, 5))

@Composable
internal fun PersonalLibraryToolbar(onSettings: () -> Unit, onLocal: () -> Unit,
    onSearch: () -> Unit, onRecognition: () -> Unit, onCreate: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MelodiaIconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "个人音乐库样式设置") }
        Spacer(Modifier.weight(1f))
        MelodiaIconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "搜索音乐") }
        MelodiaIconButton(onClick = onRecognition) { Icon(Icons.Rounded.Mic, "听歌识曲") }
        MelodiaIconButton(onClick = onCreate) { Icon(Icons.Rounded.Add, "新建或导入歌单") }
        MelodiaIconButton(onClick = onLocal) { Icon(Icons.Rounded.Folder, "本地音乐") }
    }
}

@Composable
internal fun PersonalLibraryContent(items: List<LibraryItem>, layout: LibraryPageLayout,
    onItemClick: (LibraryItem) -> Unit, onMore: (LibraryItemType) -> Unit,
    local: PersonalLocalLibrary? = null, localActions: PersonalLocalActions = PersonalLocalActions(),
    onlineStatus: @Composable () -> Unit = {}) {
    val current = layout.normalized(LibraryPage.PERSONAL)
    val groups = remember(items) { items.groupBy { it.type } }
    val recentLocal = remember(local?.library) { local?.library?.tracks.orEmpty().sortedByDescending { it.dateAddedMs } }
    LazyColumn(Modifier.fillMaxSize().testTag("personal_library_sections"),
        contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)) {
        item("online_status") { onlineStatus() }
        if (local?.status != null) item("local_status") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(local.status, style = MaterialTheme.typography.bodySmall)
                if (!local.loading) TextButton(onClick = localActions.onRetry) { Text(if (local.needsPermission) "授权访问音频" else "重新加载本地曲库") }
            }
        }
        current.sections.filter { it.visible }.forEach { section ->
            if (section.key.startsWith("local_")) {
                personalLocalSection(section.key, local ?: PersonalLocalLibrary(), localActions, recentLocal, current)
                return@forEach
            }
            val type = when (section.key) {
                "artists" -> LibraryItemType.ARTIST
                "albums" -> LibraryItemType.ALBUM
                else -> LibraryItemType.PLAYLIST
            }
            val records = current.orderedItems(section.key, groups[type].orEmpty()) { it.id }
            val title = LibraryPage.PERSONAL.sections.first { it.first == section.key }.second
            item("${section.key}_header") { LocalSectionHeader(title, onMoreClick = { onMore(type) }) }
            if (records.isEmpty()) item("${section.key}_empty") {
                Text("暂无$title", Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            else if (type == LibraryItemType.PLAYLIST) {
                val columns = current.playlistColumns
                items(playlistPreviewRows(records, columns), key = { "playlist_row_${it.first().id}" }) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { record ->
                            PersonalLibraryCell(record, current.playlistMode == "covers", columns,
                                Modifier.weight(1f), onClick = { onItemClick(record) })
                        }
                        // Only finish the current row's widths. No placeholder rows are measured.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else item("${section.key}_shelf") {
                LazyRow(modifier = Modifier.testTag(if (type == LibraryItemType.ARTIST) "personal_library_artist_shelf" else "personal_library_album_shelf"), contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (type == LibraryItemType.ARTIST) 16.dp else 8.dp)) {
                    items(current.shelfItems(section.key, records) { it.id }, key = { it.id }) { record ->
                        PersonalLibraryCover(record, Modifier.width(if (type == LibraryItemType.ARTIST) 64.dp else 108.dp),
                            onClick = { onItemClick(record) })
                    }
                }
            }
        }
        if (current.sections.none { it.visible }) item("hidden_sections") {
            Text("栏目已隐藏，可在左上角齿轮中重新显示", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LibraryRemoteCover(item: LibraryItem, modifier: Modifier) {
    SubcomposeAsyncImage(model = item.coverUrl.takeIf { it.isNotBlank() }, contentDescription = null,
        contentScale = ContentScale.Crop, loading = { CoverPlaceholder() }, error = { CoverPlaceholder() },
        modifier = modifier.clip(if (item.type == LibraryItemType.ARTIST) CircleShape else MaterialTheme.shapes.small))
}

@Composable
internal fun PersonalLibraryCell(item: LibraryItem, covers: Boolean, columns: Int, modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    if (covers) PersonalLibraryCover(item, modifier, onClick)
    else Row(modifier.heightIn(min = 64.dp).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        LibraryRemoteCover(item, Modifier.size(when { columns == 1 -> 48.dp; columns == 2 -> 36.dp; else -> 24.dp }))
        Spacer(Modifier.width(if (columns < 3) 10.dp else 4.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = if (columns < 3) 1 else 3, overflow = TextOverflow.Ellipsis,
                fontSize = if (columns < 3) 15.sp else 11.sp, fontWeight = FontWeight.Medium)
            Text(if (item.trackCount > 0) "${item.trackCount} 首" else item.subtitle,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = if (columns < 3) 12.sp else 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PersonalLibraryCover(item: LibraryItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = if (item.type == LibraryItemType.ARTIST) Alignment.CenterHorizontally else Alignment.Start) {
        LibraryRemoteCover(item, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = if (item.type == LibraryItemType.ARTIST) 12.sp else 13.sp)
        if (item.type != LibraryItemType.ARTIST) Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonalLibraryCollectionSheet(type: LibraryItemType, items: List<LibraryItem>, layout: LibraryPageLayout,
    onItemClick: (LibraryItem) -> Unit, onDismiss: () -> Unit) {
    val title = when (type) { LibraryItemType.PLAYLIST -> "歌单"; LibraryItemType.ARTIST -> "歌手"; LibraryItemType.ALBUM -> "专辑" }
    val columns = if (type == LibraryItemType.PLAYLIST) layout.playlistColumns else 3
    val key = when (type) { LibraryItemType.PLAYLIST -> "playlists"; LibraryItemType.ARTIST -> "artists"; LibraryItemType.ALBUM -> "albums" }
    val ordered = layout.orderedItems(key, items) { it.id }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.9f).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("$title · ${items.size}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            LazyVerticalGrid(GridCells.Fixed(columns), modifier = Modifier.weight(1f).testTag("personal_library_all"),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ordered, key = { it.id }) { item ->
                    PersonalLibraryCell(item, type != LibraryItemType.PLAYLIST || layout.playlistMode == "covers", columns,
                        onClick = { onDismiss(); onItemClick(item) })
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}
