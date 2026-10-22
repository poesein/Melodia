package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogSelection
import com.lin0721.linmusic.feature.artist.ui.catalog.ArtistCatalogUiState

enum class ArtistAlbumSortOrder(val label: String) {
    DEFAULT("默认顺序"), NEWEST("发布时间：从新到旧"), OLDEST("发布时间：从旧到新")
}

// 专辑 Tab: 采用精致的行模式展示，滚动到底自动追加下一页
fun LazyListScope.artistAlbumTab(
    albums: List<ArtistAlbum>,
    catalogState: ArtistCatalogUiState,
    catalogSelection: ArtistCatalogSelection?,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    sortOrder: ArtistAlbumSortOrder = ArtistAlbumSortOrder.DEFAULT,
    onSortOrderChanged: ((ArtistAlbumSortOrder) -> Unit)? = null
) {
    if (onSortOrderChanged != null) item(key = "artist_album_sort") {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }.let { expanded ->
            Box(Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.md)) {
                TextButton(onClick = { expanded.value = true }) { Text(sortOrder.label) }
                androidx.compose.material3.DropdownMenu(expanded.value, { expanded.value = false }) {
                    ArtistAlbumSortOrder.entries.forEach { order ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(order.label) },
                            onClick = { expanded.value = false; onSortOrderChanged(order) })
                    }
                }
            }
        }
    }
    // Keep browsing compact; diagnostics and all controls live in the artist menu.
    if (catalogState.enabled && catalogSelection != null) {
        item(key = "artist_catalog_summary") {
            Text(
                "匹配${catalogSelection.matchedCount}·未识别${catalogSelection.unmatchedCount}·合并${catalogSelection.duplicateCount}·排除${catalogSelection.excludedCount}",
                modifier = Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
    val ordered = when (sortOrder) {
        ArtistAlbumSortOrder.DEFAULT -> albums
        ArtistAlbumSortOrder.NEWEST -> albums.sortedByDescending { it.publishTime.takeIf { time -> time > 0L } ?: Long.MIN_VALUE }
        ArtistAlbumSortOrder.OLDEST -> albums.sortedBy { it.publishTime.takeIf { time -> time > 0L } ?: Long.MAX_VALUE }
    }
    items(ordered, key = { it.id }) { album ->
        ArtistAlbumRow(album = album, onClick = { onAlbumClick(album.id) })
    }
    if (albums.isEmpty() && !loadingMore && !hasMore && loadError == null) {
        item(key = "empty_albums") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (catalogSelection != null) "暂无匹配专辑" else "暂无专辑",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    }
    if (loadingMore || (albums.isEmpty() && hasMore && loadError == null)) {
        item(key = "albums_loading_more") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = MelodiaSpacing.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
    if (loadError != null) {
        item(key = "albums_retry") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(MelodiaSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(loadError, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                TextButton(onClick = onRetry) { Text("重试加载专辑") }
            }
        }
    }
}
