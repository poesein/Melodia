package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchRow
import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchState
import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchStatus
import com.lin0721.linmusic.feature.localmusic.domain.LocalMatchTitle
import com.lin0721.linmusic.feature.player.ui.formatTime

private enum class BatchMatchFilter(val title: String) {
    ALL("全部"), MATCHED("已匹配"), NO_MATCH("未命中"), NEEDS_CONFIRMATION("需确认"), ERROR("出错"), PENDING("待处理");

    fun accepts(status: BatchLocalMatchStatus): Boolean = when (this) {
        ALL -> true
        MATCHED -> status == BatchLocalMatchStatus.MATCHED || status == BatchLocalMatchStatus.ALREADY_LINKED
        NO_MATCH -> status == BatchLocalMatchStatus.NO_MATCH
        NEEDS_CONFIRMATION -> status == BatchLocalMatchStatus.NEEDS_CONFIRMATION
        ERROR -> status == BatchLocalMatchStatus.ERROR
        PENDING -> status == BatchLocalMatchStatus.PENDING
    }
}

/** The repository owns progress and results so dismissing this sheet does not lose the report. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchLocalMatchSheet(
    state: BatchLocalMatchState,
    onDismiss: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onRetryUnmatched: () -> Unit,
    onRefresh: () -> Unit,
    onManualAssociation: (BatchLocalMatchRow) -> Unit
) {
    var filter by remember { mutableStateOf(BatchMatchFilter.ALL) }
    val counts = remember(state.rows) {
        BatchMatchFilter.entries.associateWith { current -> state.rows.count { current.accepts(it.status) } }
    }
    val visibleRows = remember(state.rows, filter) { state.rows.filter { filter.accepts(it.status) } }
    val pendingCount = counts[BatchMatchFilter.PENDING] ?: 0
    val errorCount = counts[BatchMatchFilter.ERROR] ?: 0
    val resumableCount = pendingCount + errorCount
    val retryCount = (counts[BatchMatchFilter.NO_MATCH] ?: 0) +
        (counts[BatchMatchFilter.NEEDS_CONFIRMATION] ?: 0) + (counts[BatchMatchFilter.ERROR] ?: 0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("批量匹配网易云", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "只自动关联唯一且版本一致的结果。搜索忽略「01 五味瓶」中的序号，原文件和标签保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "已处理 ${state.processedCount} / ${state.totalCount} 首 · 已匹配 ${counts[BatchMatchFilter.MATCHED] ?: 0} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LinearProgressIndicator(
                    progress = { if (state.totalCount == 0) 0f else state.processedCount.toFloat() / state.totalCount },
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.isLoading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("正在读取曲库和匹配记录…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.message?.takeIf(String::isNotBlank)?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall,
                        color = if ((counts[BatchMatchFilter.ERROR] ?: 0) > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    when {
                        state.isRunning -> "关闭页面仍会继续；网络或服务出错会暂停。"
                        (counts[BatchMatchFilter.ERROR] ?: 0) > 0 -> "有歌曲因网络或服务错误未完成，请稍后继续或重试未匹配。"
                        pendingCount > 0 -> "可继续处理剩余歌曲，匹配记录已保存。"
                        state.totalCount > 0 -> "本轮已处理；未匹配的歌曲可手动核对。"
                        else -> "请先扫描或导入本地音频，再刷新曲库。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isRunning) {
                        Button(onClick = onPause, enabled = !state.isLoading) { Text("暂停") }
                    } else {
                        Button(onClick = onStartOrResume, enabled = resumableCount > 0 && !state.isLoading) {
                            Text(if (errorCount > 0 && pendingCount == 0) "重试出错" else if (state.processedCount > 0) "继续匹配" else "开始匹配")
                        }
                    }
                    TextButton(onClick = onRetryUnmatched, enabled = !state.isRunning && !state.isLoading && retryCount > 0) {
                        Text("重试未匹配")
                    }
                    TextButton(onClick = onRefresh, enabled = !state.isRunning && !state.isLoading) { Text("刷新曲库") }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BatchMatchFilter.entries) { item ->
                    FilterChip(selected = filter == item, onClick = { filter = item },
                        label = { Text("${item.title} ${counts[item] ?: 0}") })
                }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                if (visibleRows.isEmpty()) item {
                    Text(if (state.totalCount == 0) "还没有可处理的本地歌曲。" else "此分类没有歌曲。",
                        modifier = Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(visibleRows, key = { it.local.uri.toString() }) { row ->
                    BatchLocalMatchResultRow(row, onManualAssociation)
                    HorizontalDivider()
                }
                item { Spacer(Modifier.padding(bottom = 12.dp)) }
            }
        }
    }
}

@Composable
private fun BatchLocalMatchResultRow(row: BatchLocalMatchRow, onManualAssociation: (BatchLocalMatchRow) -> Unit) {
    val linked = row.status == BatchLocalMatchStatus.MATCHED || row.status == BatchLocalMatchStatus.ALREADY_LINKED
    val statusText = when (row.status) {
        BatchLocalMatchStatus.PENDING -> "待处理"
        BatchLocalMatchStatus.MATCHED -> "已匹配"
        BatchLocalMatchStatus.ALREADY_LINKED -> "已有关联"
        BatchLocalMatchStatus.NO_MATCH -> "未命中"
        BatchLocalMatchStatus.NEEDS_CONFIRMATION -> "需确认"
        BatchLocalMatchStatus.ERROR -> "出错"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.local.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(statusText, style = MaterialTheme.typography.labelMedium,
                color = when (row.status) {
                    BatchLocalMatchStatus.ERROR -> MaterialTheme.colorScheme.error
                    BatchLocalMatchStatus.MATCHED, BatchLocalMatchStatus.ALREADY_LINKED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                })
        }
        Text("${row.local.artist} · ${row.local.album?.takeIf(String::isNotBlank) ?: "专辑未知"} · ${formatTime(row.local.durationMs)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!linked || LocalMatchTitle.forSearch(row.local.title) != row.local.title.trim()) {
            Text("搜索：${row.query}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!linked && row.reason.isNotBlank()) Text(row.reason, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        row.matchedTrack?.let { BatchOnlineTrackDetails(it) }
        TextButton(onClick = { onManualAssociation(row) }) {
            Text(when {
                linked -> "查看或更换关联"
                row.candidates.isNotEmpty() -> "查看 ${row.candidates.size} 个候选并关联"
                else -> "手动关联"
            })
        }
    }
}

@Composable
private fun BatchOnlineTrackDetails(track: Track) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("网易云：${track.name} · ${track.ar.joinToString(" / ") { it.name }}",
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${track.al.name.ifBlank { "专辑未知" }} · ${track.dt.takeIf { it > 0L }?.let(::formatTime) ?: "时长未知"}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
