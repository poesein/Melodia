package com.lin0721.linmusic.feature.artist.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistAlbumCorrectionsRepository
import com.lin0721.linmusic.feature.artist.domain.catalog.AlbumArtistCorrectionShortcut
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistAlbumCorrections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AlbumArtistCorrectionHost(album: PlaylistDetail, onDismiss: () -> Unit,
    repository: ArtistAlbumCorrectionsRepository = koinInject()) {
    val artists = remember(album) { AlbumArtistCorrectionShortcut.artists(album) }
    var selected by remember(album.id) { mutableStateOf(artists.singleOrNull()) }
    var saved by remember { mutableStateOf<ArtistAlbumCorrections?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selected?.id) {
        saved = null; error = null
        selected?.let { artist ->
            try { saved = repository.observe(artist.id).first() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "无法读取已保存修正，未覆盖原记录" }
        }
    }
    AlbumArtistCorrectionDialog(album.name, artists, selected, saved != null, saved?.enabled == false,
        saving, error, onSelected = { selected = it }, onDismiss = onDismiss, onSave = {
            val target = selected
            if (target != null && saved != null && !saving) {
                saving = true
                scope.launch {
                    try {
                        repository.setDecision(target.id, AlbumArtistCorrectionShortcut.snapshot(album), true, enableCorrections = true)
                        ToastManager.showToast("已添加到 ${target.name} 的专辑页")
                        onDismiss()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "保存失败，请重试" }
                    finally { saving = false }
                }
            }
        })
}

@Composable
internal fun AlbumArtistCorrectionDialog(albumName: String, artists: List<Artist>, selected: Artist?,
    loaded: Boolean, resumesSavedChoices: Boolean, saving: Boolean, error: String?,
    onSelected: (Artist) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("显示在歌手专辑页") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(albumName)
            Text(if (artists.size > 1) "选择要添加到的歌手；只保存你选中的这一位。" else "保存为该歌手的手动添加记录。")
            if (artists.isEmpty()) Text("专辑缺少有效的署名歌手，请重新加载专辑详情后再试。")
            LazyColumn(Modifier.heightIn(max = 240.dp)) {
                items(artists, key = { it.id }) { artist ->
                    Row(Modifier.fillMaxWidth().selectable(selected = artist.id == selected?.id,
                        enabled = !saving, role = Role.RadioButton, onClick = { onSelected(artist) }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = artist.id == selected?.id, onClick = null)
                        Text(artist.name, Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (resumesSavedChoices) Text("此歌手的手动修正已暂停。添加后将重新应用此前保存的添加和隐藏记录。",
                style = MaterialTheme.typography.bodySmall)
            if (selected != null && !loaded && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(enabled = selected != null && loaded && !saving, onClick = onSave) {
            Text(if (saving) "正在保存…" else if (resumesSavedChoices) "添加并启用手动修正" else "添加")
        }
    }, dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } })
}
