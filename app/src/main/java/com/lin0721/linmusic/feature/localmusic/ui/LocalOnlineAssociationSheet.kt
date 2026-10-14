package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.feature.player.ui.formatTime

/** Search is only a proposal: the user chooses and confirms the recording/version. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalOnlineAssociationSheet(
    state: LocalOnlineAssociationState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onConfirm: (Track) -> Unit
) {
    var selected by remember(state.track.uri) { mutableStateOf<Track?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f).navigationBarsPadding()
                .imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (state.track.songId == null) "关联网易云歌曲" else "更换网易云关联",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("本地：${state.track.title}\n${state.track.artist}\n${state.track.album?.takeIf { it.isNotBlank() } ?: "专辑未知"} · ${formatTime(state.track.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("搜索后选择同一首歌和版本，播放仍使用原本地文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { selected = null; onQueryChange(it) },
                    label = { Text("歌名 / 歌手") },
                    singleLine = true,
                    enabled = !state.saving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSearch, enabled = state.query.isNotBlank() && !state.saving,
                    modifier = Modifier.padding(top = 8.dp)) { Text("搜索") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (state.searching) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("正在搜索网易云…", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!state.searching && state.searchedQuery.isNotBlank() && state.results.isEmpty() && state.error == null) {
                Text("未找到歌曲，请调整歌名或歌手后重试。", style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.results, key = { it.id }) { track ->
                    Column(Modifier.fillMaxWidth().clickable(enabled = !state.saving) { selected = track }
                        .padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(track.name, fontWeight = FontWeight.SemiBold)
                        Text(track.ar.joinToString(" / ") { it.name },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("${track.al.name.ifBlank { "专辑未知" }} · ${track.dt.takeIf { it > 0L }?.let(::formatTime) ?: "时长未知"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
                if (state.hasMore) item {
                    TextButton(onClick = onLoadMore, enabled = !state.searching && !state.saving,
                        modifier = Modifier.fillMaxWidth()) { Text("加载更多歌曲") }
                }
            }
        }
    }
    selected?.let { online ->
        AlertDialog(
            onDismissRequest = { if (!state.saving) selected = null },
            title = { Text("确认歌曲与版本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("本地音频\n${state.track.title}\n${state.track.artist}\n${state.track.album.orEmpty()} · ${formatTime(state.track.durationMs)}")
                    Text("网易云歌曲\n${online.name}\n${online.ar.joinToString(" / ") { it.name }}\n${online.al.name} · ${online.dt.takeIf { it > 0L }?.let(::formatTime) ?: "时长未知"}")
                    Text(if (state.track.songId == null) "确认关联后，这首网易云歌曲将优先播放该本地音频。"
                        else "确认后将替换该本地文件原有的网易云关联。")
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { onConfirm(online) }, enabled = !state.saving) {
                Text(if (state.saving) "正在关联…" else "确认关联")
            } },
            dismissButton = { TextButton(onClick = { selected = null }, enabled = !state.saving) { Text("重新选择") } }
        )
    }
}
