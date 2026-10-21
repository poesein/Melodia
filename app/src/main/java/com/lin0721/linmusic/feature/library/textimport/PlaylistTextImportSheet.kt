package com.lin0721.linmusic.feature.library.textimport

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistTextImportSheet(onDismiss: () -> Unit, onImported: (Long) -> Unit) {
    val viewModel: PlaylistTextImportViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var onlyUnresolved by remember { mutableStateOf(false) }
    val saving by rememberUpdatedState(state.saving)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { !saving })

    LaunchedEffect(viewModel) { viewModel.reset() }
    DisposableEffect(viewModel) { onDispose { viewModel.reset() } }
    LaunchedEffect(state.finishedPlaylistId) {
        state.finishedPlaylistId?.let { id ->
            viewModel.consumeFinished(id)
            onImported(id)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            reading = true
            try {
                val (name, bytes) = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "歌单.txt"
                    require(name.endsWith(".txt", ignoreCase = true)) { "请选择 .txt 文件" }
                    val bytes = resolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= PlaylistTextDocument.MAX_BYTES) { "TXT 文件不能超过 1 MiB" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("无法读取文件")
                    name.take(120) to bytes
                }
                onlyUnresolved = false
                viewModel.loadDocument(bytes, name)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                viewModel.showReadError((error as? IllegalArgumentException)?.message ?: "无法读取文件，请重新选择")
            } finally { reading = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!state.saving) onDismiss() }, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface, shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("导入歌单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) },
                    enabled = !reading && !state.loading && !state.matching && !state.selectionLocked) {
                    Text(if (state.rows.isEmpty()) "选择 TXT" else "更换文件")
                }
            }
            Text("每行：歌名 - 歌手。匹配后确认，创建到当前网易云账户。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.fileName.isNotBlank()) Text(state.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            if (reading || state.loading || state.matching || state.saving) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Text(when {
                    state.saving -> "正在导入，请稍候…"
                    reading || state.loading -> "正在读取文件…"
                    else -> "正在匹配 ${state.rows.count { it.status != TextPlaylistStatus.PENDING }}/${state.rows.size}"
                }, style = MaterialTheme.typography.labelMedium)
            }
            if (state.rows.isNotEmpty()) {
                val matched = state.rows.count { it.selected != null }
                val unresolved = state.rows.count { it.status in unresolvedStatuses }
                val skipped = state.rows.count { it.status in setOf(TextPlaylistStatus.INVALID, TextPlaylistStatus.DUPLICATE) }
                Text("已匹配 $matched · 待处理 $unresolved · 跳过 $skipped",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !onlyUnresolved, onClick = { onlyUnresolved = false }, label = { Text("全部") })
                    FilterChip(selected = onlyUnresolved, onClick = { onlyUnresolved = true }, label = { Text("待处理") })
                    if (state.matching) TextButton(onClick = viewModel::stopMatching) { Text("停止匹配") }
                    else if (unresolved > 0) TextButton(onClick = viewModel::retryUnresolved,
                        enabled = !state.matching && !state.selectionLocked) { Text("重试未命中") }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)) }
            val visible = if (onlyUnresolved) state.rows.filter { it.status in unresolvedStatuses } else state.rows
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.rows.isEmpty()) item {
                    Text("例如：\nMap of the Problematique - Muse\nStarlight - Muse\n\n支持 UTF-8、UTF-16（带 BOM）和 GB18030，最多 500 行。\n只自动选择歌名、歌手一致的结果；其他结果由你确认。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp))
                }
                items(visible, key = { it.line }) { row ->
                    ImportSongRow(row, enabled = !state.selectionLocked && !state.matching,
                        onInclude = { viewModel.include(row.line, it) },
                        onSelect = { viewModel.select(row.line, it) },
                        onSearch = { viewModel.searchRow(row.line, it) })
                }
                if (onlyUnresolved && visible.isEmpty()) item { Text("没有待处理项", modifier = Modifier.padding(20.dp)) }
            }
            if (state.rows.isNotEmpty()) {
                OutlinedTextField(value = state.name, onValueChange = viewModel::setName, label = { Text("歌单名称") },
                    enabled = !state.selectionLocked, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("私密歌单", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.isPrivate, onCheckedChange = viewModel::setPrivate, enabled = !state.selectionLocked)
                }
                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth(),
                    enabled = !reading && !state.loading && !state.matching && !state.saving && state.selectedCount > 0 && state.name.isNotBlank()) {
                    Text(if (state.createdPlaylistId == null) "导入 ${state.selectedCount} 首到网易云" else "继续添加到已创建歌单")
                }
                Text("仅导入勾选歌曲，重复歌曲只保留一次；未选中的条目会跳过。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            }
        }
    }
}

private val unresolvedStatuses = setOf(TextPlaylistStatus.PENDING, TextPlaylistStatus.REVIEW, TextPlaylistStatus.NOT_FOUND, TextPlaylistStatus.ERROR)

@Composable
private fun ImportSongRow(row: TextPlaylistRow, enabled: Boolean, onInclude: (Boolean) -> Unit,
    onSelect: (com.lin0721.linmusic.core.model.Track) -> Unit, onSearch: (String) -> Unit) {
    var expanded by remember(row.line) { mutableStateOf(false) }
    var query by remember(row.line) { mutableStateOf("${row.title} ${row.artist}".trim()) }
    val editable = row.status !in setOf(TextPlaylistStatus.INVALID, TextPlaylistStatus.DUPLICATE)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(enabled = editable) { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = row.selected != null && row.included, onCheckedChange = onInclude,
                enabled = enabled && row.selected != null)
            Column(Modifier.weight(1f)) {
                Text("${row.line}. ${row.title.ifBlank { row.original }}", maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(row.selected?.let { "${it.ar.joinToString(" / ") { a -> a.name }} · ${it.al.name}" }
                    ?: row.message ?: if (row.status == TextPlaylistStatus.PENDING) "等待匹配 · ${row.artist}" else row.artist,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editable) Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起候选" else "查看候选")
        }
        if (expanded && editable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = query, onValueChange = { query = it.take(200) }, label = { Text("搜索歌名、歌手") },
                    enabled = enabled, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = { onSearch(query) }, enabled = enabled && query.isNotBlank()) { Text("搜索") }
            }
            row.candidates.forEach { candidate ->
                Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onSelect(candidate); expanded = false }
                    .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = candidate.al.picUrl, contentDescription = null, modifier = Modifier.size(40.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(candidate.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${candidate.ar.joinToString(" / ") { it.name }} · ${candidate.al.name} · ${candidate.dt / 60000}:${(candidate.dt / 1000 % 60).toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (row.selected?.id == candidate.id) Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
