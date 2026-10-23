package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import com.lin0721.linmusic.core.ui.components.SelectionAction

/** One action set for system likes, owned lists, and read-only playlists. */
internal fun playlistSelectionActions(
    canRemove: Boolean,
    playNext: () -> Unit,
    addToPlaylist: () -> Unit,
    like: () -> Unit,
    unlike: () -> Unit,
    download: () -> Unit,
    remove: () -> Unit
): List<SelectionAction> = buildList {
    add(SelectionAction("下一首播放", Icons.AutoMirrored.Filled.QueueMusic, playNext))
    add(SelectionAction("添加到歌单", Icons.AutoMirrored.Filled.PlaylistAdd, addToPlaylist))
    add(SelectionAction("加入喜欢", Icons.Default.FavoriteBorder, like))
    add(SelectionAction("取消喜欢", Icons.Default.Favorite, unlike))
    add(SelectionAction("下载", Icons.Default.Download, download))
    if (canRemove) add(SelectionAction("移出歌单", Icons.Default.Delete, remove))
}
