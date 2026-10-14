package com.lin0721.linmusic.feature.localmusic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.feature.localmusic.data.PreferredLocalAudioRepository
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.playlist.ui.OptionRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Reused by every online-song menu; selecting a document keeps the original file in place. */
@Composable
fun LocalAudioLinkOptions(songId: Long, onlineTrack: Track? = null) {
    if (songId <= 0L) return
    val repository: PreferredLocalAudioRepository = koinInject()
    val scope = rememberCoroutineScope()
    val manualUri by remember(repository, songId) { repository.manualUri(songId) }.collectAsStateWithLifecycle(initialValue = null)
    val automaticExcluded by remember(repository, songId) { repository.automaticExcluded(songId) }.collectAsStateWithLifecycle(initialValue = false)
    val preferLocal by repository.enabled.collectAsStateWithLifecycle(initialValue = true)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val linked = repository.link(songId, uri, onlineTrack)
            ToastManager.showToast(when {
                !linked -> "无法关联：请选择可读取的音频文件"
                preferLocal -> "已关联本地音频，下次播放时优先读取"
                else -> "已关联；请在播放与下载设置中开启「优先使用本地音频」"
            })
        }
    }
    OptionRow(
        icon = Icons.Rounded.Link,
        text = if (manualUri == null) "关联本地音频（优先播放）" else "更换关联的本地音频",
        onClick = { picker.launch(arrayOf("audio/*")) }
    )
    if (manualUri != null) OptionRow(
        icon = Icons.Rounded.LinkOff,
        text = "解除本地关联",
        onClick = {
            scope.launch {
                repository.unlink(songId)
                ToastManager.showToast("已解除关联，并停用本曲自动匹配；原文件保留")
            }
        }
    )
    if (automaticExcluded) OptionRow(
        icon = Icons.Rounded.Restore,
        text = "恢复本曲自动匹配",
        onClick = { scope.launch { repository.allowAutomaticMatch(songId) } }
    )
}
