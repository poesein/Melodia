package com.lin0721.linmusic.feature.personallibrary

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun LibraryPageLayoutEditor(page: LibraryPage, state: LibraryPageLayoutState,
    onManageSection: ((String) -> Unit)? = null,
    onSave: ((LibraryPageLayout) -> LibraryPageLayout) -> Unit) {
    val enabled = state.loaded && !state.saving
    val layout = state.layout.normalized(page)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("栏目排序与显示", style = MaterialTheme.typography.titleMedium)
        layout.sections.forEachIndexed { index, section ->
            val title = page.sections.first { it.first == section.key }.second
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f))
                IconButton(enabled = enabled && index > 0,
                    onClick = { onSave { it.moved(page, section.key, -1) } }) {
                    Icon(Icons.Rounded.ArrowUpward, "上移$title")
                }
                IconButton(enabled = enabled && index < layout.sections.lastIndex,
                    onClick = { onSave { it.moved(page, section.key, 1) } }) {
                    Icon(Icons.Rounded.ArrowDownward, "下移$title")
                }
                Switch(checked = section.visible, enabled = enabled,
                    onCheckedChange = { show -> onSave { current -> current.copy(sections = current.sections.map { if (it.key == section.key) it.copy(visible = show) else it }) } },
                    modifier = Modifier.minimumInteractiveComponentSize().semantics { contentDescription = "显示$title" })
            }
            if (supportsItemOrder(section.key) && onManageSection != null) {
                TextButton(enabled = enabled, onClick = { onManageSection(section.key) }) {
                    val count = layout.itemLimit(section.key)
                    Text(if (supportsItemLimit(section.key)) "${if (count == 0) "显示全部" else "显示 $count 项"} · 调整${title}顺序" else "调整${title}顺序")
                }
            }
        }
        if (page == LibraryPage.PERSONAL) {
            HorizontalDivider()
            Text("歌单显示", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("list" to "列表", "covers" to "封面").forEach { (mode, title) ->
                    FilterChip(selected = layout.playlistMode == mode, enabled = enabled,
                        onClick = { onSave { it.copy(playlistMode = mode) } }, label = { Text(title) })
                }
            }
            Text(if (layout.playlistMode == "list") "列表列数" else "封面列数", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { count ->
                    FilterChip(selected = layout.playlistColumns == count, enabled = enabled,
                        onClick = { onSave { if (it.playlistMode == "list") it.copy(listColumns = count) else it.copy(coverColumns = count) } },
                        label = { Text("$count") })
                }
            }
            Text("最多预览 5 行，按实际内容收起；列表与封面分别保存列数（1–5 列）", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!state.loaded && state.error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.saving) Text("正在保存…", style = MaterialTheme.typography.bodySmall)
    }
}
