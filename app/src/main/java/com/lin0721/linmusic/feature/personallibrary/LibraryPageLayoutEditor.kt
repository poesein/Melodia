package com.lin0721.linmusic.feature.personallibrary

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaSwitch
import com.lin0721.linmusic.feature.settings.ui.SettingsRow

/** Uses the same rows, typography and switches as the other settings pages. */
@Composable
fun LibraryPageLayoutEditor(page: LibraryPage, state: LibraryPageLayoutState,
    onManageSection: ((String) -> Unit)? = null,
    onSave: ((LibraryPageLayout) -> LibraryPageLayout) -> Unit) {
    val enabled = state.loaded && !state.saving
    val layout = state.layout.normalized(page)
    var dialog by remember { mutableStateOf<String?>(null) }
    Column {
        layout.sections.forEachIndexed { index, section ->
            val title = page.sections.first { it.first == section.key }.second
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(if (section.visible) "已显示 · 第 ${index + 1} 栏" else "已隐藏",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                IconButton(enabled = enabled && index > 0,
                    onClick = { onSave { it.moved(page, section.key, -1) } }) {
                    Icon(Icons.Rounded.ArrowUpward, "上移$title", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(enabled = enabled && index < layout.sections.lastIndex,
                    onClick = { onSave { it.moved(page, section.key, 1) } }) {
                    Icon(Icons.Rounded.ArrowDownward, "下移$title", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MelodiaSwitch(checked = section.visible, enabled = enabled,
                    onCheckedChange = { show -> onSave { current -> current.copy(sections = current.sections.map {
                        if (it.key == section.key) it.copy(visible = show) else it
                    }) } }, modifier = Modifier.semantics { contentDescription = "显示$title" })
            }
            if (supportsItemOrder(section.key) && onManageSection != null) {
                val count = layout.itemLimit(section.key)
                SettingsRow("${title}数量与顺序",
                    if (supportsItemLimit(section.key)) "${if (count == 0) "显示全部" else "显示 $count 项"} · 手动排列内容" else "手动排列内容",
                    { if (enabled) onManageSection(section.key) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        }
        if (page == LibraryPage.PERSONAL) {
            SettingsRow("歌单显示", if (layout.playlistMode == "list") "列表" else "封面",
                { if (enabled) dialog = "mode" })
            SettingsRow(if (layout.playlistMode == "list") "列表列数" else "封面列数",
                "${layout.playlistColumns} 列 · 最多预览 5 行，按内容收起", { if (enabled) dialog = "columns" })
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!state.loaded && state.error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.saving) Text("正在保存…", style = MaterialTheme.typography.bodySmall)
    }
    dialog?.let { kind ->
        val options = if (kind == "mode") listOf("list" to "列表", "covers" to "封面")
            else (1..5).map { it.toString() to "$it 列" }
        val current = if (kind == "mode") layout.playlistMode else layout.playlistColumns.toString()
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(if (kind == "mode") "歌单显示" else "每行列数") },
            text = { Column { options.forEach { (value, label) ->
                TextButton(enabled = enabled, modifier = Modifier.fillMaxWidth(), onClick = {
                    onSave { if (kind == "mode") it.copy(playlistMode = value)
                        else if (it.playlistMode == "list") it.copy(listColumns = value.toInt()) else it.copy(coverColumns = value.toInt()) }
                    dialog = null
                }) { Text(label + if (value == current) " · 当前" else "") }
            } } }, confirmButton = {})
    }
}
