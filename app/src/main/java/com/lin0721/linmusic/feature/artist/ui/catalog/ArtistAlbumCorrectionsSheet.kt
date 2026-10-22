package com.lin0721.linmusic.feature.artist.ui.catalog

import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lin0721.linmusic.core.ui.scroll.rememberExpandedSheetScrollBoundary
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistAlbumCorrectionsSheet(
    artistName: String,
    sourceAlbums: List<ArtistAlbum>,
    automaticAlbums: List<ArtistAlbum>,
    state: ArtistAlbumCorrectionsState,
    catalogEnabled: Boolean,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadError: String?,
    onRetryAlbums: () -> Unit,
    onRetryRecords: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onDecision: (ArtistAlbum, Boolean) -> Unit,
    onEntryActive: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val boundary = rememberExpandedSheetScrollBoundary(sheetState) { listState.canScrollForward }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background, shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }) {
        ArtistAlbumCorrectionsContent(artistName, sourceAlbums, automaticAlbums, state, catalogEnabled,
            hasMore, loadingMore, loadError, onRetryAlbums, onRetryRecords, onEnabledChanged,
            onDecision, onEntryActive, onDismiss,
            Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding().nestedScroll(boundary), listState)
    }
}

/** One scrolling list retains its position during one-tap corrections; no nested scrolling sheet. */
@Composable
internal fun ArtistAlbumCorrectionsContent(
    artistName: String,
    sourceAlbums: List<ArtistAlbum>,
    automaticAlbums: List<ArtistAlbum>,
    state: ArtistAlbumCorrectionsState,
    catalogEnabled: Boolean,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadError: String?,
    onRetryAlbums: () -> Unit,
    onRetryRecords: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onDecision: (ArtistAlbum, Boolean) -> Unit,
    onEntryActive: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    var query by rememberSaveable(state.artistId) { mutableStateOf("") }
    var filter by rememberSaveable(state.artistId) { mutableIntStateOf(0) }
    val choices = state.value
    val albums = remember(sourceAlbums, choices) { choices.editableAlbums(sourceAlbums) }
    val visibleIds = remember(automaticAlbums, sourceAlbums, choices, catalogEnabled) {
        choices.apply(automaticAlbums, sourceAlbums, catalogEnabled).mapTo(hashSetOf()) { it.id }
    }
    val records = remember(choices) { choices.entries.associateBy { it.album.id } }
    val filtered = remember(albums, query, filter, visibleIds, records) {
        albums.filter { album ->
            (query.isBlank() || (listOf(album.name) + album.alias.orEmpty() + album.transNames.orEmpty())
                .any { it.contains(query.trim(), ignoreCase = true) }) &&
                when (filter) {
                    1 -> album.id in visibleIds
                    2 -> album.id !in visibleIds
                    3 -> album.id in records
                    else -> true
                }
        }
    }
    val editable = state.loaded && !state.saving
    Column(modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("手动修正专辑", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("$artistName · 仅调整此歌手的展示列表", style = MaterialTheme.typography.bodySmall)
        LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("album_corrections_list")) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("应用手动修正")
                            Text("已保存 ${records.size} 项 · 关闭后仍保留", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = choices.enabled, onCheckedChange = onEnabledChanged, enabled = editable,
                            modifier = Modifier.semantics { contentDescription = "应用手动修正" })
                    }
                    if (records.isNotEmpty()) TextButton(onClick = { onEnabledChanged(!choices.enabled) }, enabled = editable) {
                        Text(if (choices.enabled) "恢复自动筛选（保留记录）" else "重新应用已保存修正")
                    }
                    if (!catalogEnabled) Text("主要录音室筛选已关闭，手动记录暂不应用；重新开启筛选后继续生效。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else if (!choices.enabled) Text("当前使用自动结果。重新应用即可恢复此前添加和隐藏的专辑。",
                        style = MaterialTheme.typography.bodySmall)
                    else Text("手动添加优先于资料库分类和最低曲数；记录随修改自动保存。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.error?.let { message ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                            if (!state.loaded) TextButton(onClick = onRetryRecords) { Text("重试记录") }
                        }
                    }
                    if (!state.loaded && state.error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.saving) Text("正在保存…", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索此歌手的专辑") }, singleLine = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("全部", "已显示", "已隐藏", "手动记录").forEachIndexed { index, title ->
                            FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(title) })
                        }
                    }
                    Text(if (hasMore) "已读取 ${sourceAlbums.size} 张，继续加载全部专辑…" else "共 ${sourceAlbums.size} 张网易云专辑 · 当前结果 ${filtered.size} 张",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (loadError != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(loadError, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onRetryAlbums, enabled = !loadingMore) { Text("重试加载") }
                    }
                }
            }
            items(filtered, key = { it.id }) { album ->
                val record = records[album.id]
                val visible = album.id in visibleIds
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubcomposeAsyncImage(model = album.picUrl.takeIf { it.isNotBlank() }?.let { "$it?param=100y100" },
                            contentDescription = null, contentScale = ContentScale.Crop,
                            loading = { CoverPlaceholder() }, error = { CoverPlaceholder() },
                            modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(buildString {
                                append(if (album.size > 0) "${album.size} 首 · " else "")
                                append(when {
                                    record != null && (!record.active || !choices.enabled || !catalogEnabled) ->
                                        "已保存${if (record.show) "添加" else "隐藏"}，暂未应用"
                                    record != null -> if (record.show) "手动添加" else "手动隐藏"
                                    visible -> "自动显示"
                                    else -> "自动未显示"
                                })
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onDecision(album, !visible) }, enabled = editable && choices.enabled && catalogEnabled,
                            modifier = Modifier.semantics { contentDescription = "${if (visible) "隐藏" else "添加"}专辑：${album.name}" }) {
                            Text(if (visible) "隐藏" else "添加")
                        }
                    }
                    if (record != null) TextButton(onClick = { onEntryActive(album.id, !record.active) }, enabled = editable) {
                        Text(if (record.active) "此项跟随自动（保留记录）" else "恢复此项修正")
                    }
                }
                HorizontalDivider()
            }
            if (filtered.isEmpty()) item { Text(if (hasMore) "继续查找中…" else "没有符合条件的专辑",
                Modifier.padding(vertical = 20.dp), style = MaterialTheme.typography.bodyMedium) }
            if (loadingMore) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
    }
}
