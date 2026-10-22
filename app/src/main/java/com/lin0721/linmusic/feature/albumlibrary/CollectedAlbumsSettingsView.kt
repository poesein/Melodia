package com.lin0721.linmusic.feature.albumlibrary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.feature.settings.ui.SettingsGroupCard
import com.lin0721.linmusic.feature.settings.ui.SettingsRow
import com.lin0721.linmusic.feature.settings.ui.SettingsSwitchRow
import org.koin.androidx.compose.koinViewModel

@Composable
fun CollectedAlbumsSettingsView(viewModel: CollectedAlbumsLayoutViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var choosingColumns by remember { mutableStateOf(false) }
    var choosingSort by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)) {
        if (state.error != null) item {
            Text(state.error!!, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::retry) { Text("重新读取") }
        }
        if (state.loaded) {
            item { SettingsGroupCard("封面与浏览") {
                SettingsRow("每行专辑数", "${state.layout.columns} 列", { choosingColumns = true })
                SettingsSwitchRow("按歌手分组", "同一张合辑保持完整", state.layout.groupByArtist) { enabled ->
                    viewModel.save { it.copy(groupByArtist = enabled) }
                }
                SettingsRow("专辑排序", if (state.layout.sort == AlbumPageSort.NAME) "专辑名称" else "最近收藏", { choosingSort = true })
            } }
        } else if (state.error == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
    if (choosingColumns) AlertDialog(onDismissRequest = { choosingColumns = false }, title = { Text("每行专辑数") },
        text = { Column { (1..5).forEach { columns ->
            TextButton(onClick = { viewModel.save { it.copy(columns = columns) }; choosingColumns = false }, modifier = Modifier.fillMaxWidth()) {
                Text("$columns 列${if (state.layout.columns == columns) " · 当前" else ""}")
            }
        } } }, confirmButton = {})
    if (choosingSort) AlertDialog(onDismissRequest = { choosingSort = false }, title = { Text("专辑排序") },
        text = { Column { AlbumPageSort.entries.forEach { sort ->
            TextButton(onClick = { viewModel.save { it.copy(sort = sort) }; choosingSort = false }, modifier = Modifier.fillMaxWidth()) {
                Text(if (sort == AlbumPageSort.NAME) "专辑名称" else "最近收藏")
            }
        } } }, confirmButton = {})
}
