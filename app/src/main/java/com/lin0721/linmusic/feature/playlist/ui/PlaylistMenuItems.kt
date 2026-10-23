package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

internal data class PlaylistMenuItem(
    val icon: ImageVector,
    val title: String,
    val isDestructive: Boolean = false,
    val enabled: Boolean = true,
    val subtitle: String? = null,
    val onClick: () -> Unit
)

/** One set of editing entries for the playlist's three-dot menu. */
internal fun playlistEditingMenuItems(
    isManageable: Boolean,
    isTracksEmpty: Boolean,
    sortOrder: PlaylistTrackSort,
    onEditSongsClick: () -> Unit,
    onAddMusicClick: () -> Unit,
    onEditOrderClick: () -> Unit,
    onSortClick: () -> Unit,
    onEditInfoClick: () -> Unit,
    onSortOrderChange: (PlaylistTrackSort) -> Unit
): List<PlaylistMenuItem> = buildList {
    add(PlaylistMenuItem(Icons.Default.Edit, "编辑歌曲", enabled = !isTracksEmpty, onClick = onEditSongsClick))
    if (isManageable) {
        add(PlaylistMenuItem(Icons.Default.Add, "添加音乐", onClick = onAddMusicClick))
        add(PlaylistMenuItem(Icons.AutoMirrored.Filled.Sort, "调整顺序", enabled = !isTracksEmpty) {
            // Manual reordering edits the real playlist, so leave the locally sorted view first.
            onSortOrderChange(PlaylistTrackSort())
            onEditOrderClick()
        })
    }
    add(PlaylistMenuItem(Icons.Default.FilterList, "歌曲排序", enabled = !isTracksEmpty,
        subtitle = sortOrder.label.takeIf { sortOrder != PlaylistTrackSort() }, onClick = onSortClick))
    if (isManageable) {
        add(PlaylistMenuItem(Icons.Default.Edit, "编辑歌单信息", onClick = onEditInfoClick))
    }
}

@Composable
internal fun PlaylistMenuRow(item: PlaylistMenuItem) {
    val color = when {
        !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        item.isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = item.enabled, onClick = item.onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, contentDescription = null,
            tint = if (item.enabled && !item.isDestructive) MaterialTheme.colorScheme.onSurfaceVariant else color,
            modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(MelodiaSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = color, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
