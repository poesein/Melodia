package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import java.text.Collator
import java.util.Locale

internal fun AlbumSubItem.artistLabel(): String = artists.map { it.name }.filter { it.isNotBlank() }
    .joinToString(" • ").ifBlank { "未知歌手" }

internal fun AlbumSubItem.libraryItem() = LibraryItem(
    id = id.toString(), title = name, subtitle = "专辑 · ${artistLabel()}", coverUrl = picUrl,
    type = LibraryItemType.ALBUM, updateTime = subTime, trackCount = size
)

/** Stable ordering is shared by the visible grid and its two playback actions. */
internal fun displayedAlbums(albums: List<AlbumSubItem>, query: String, layout: AlbumPageLayout): List<AlbumSubItem> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val filtered = albums.filter { album -> terms.all { term ->
        album.name.lowercase().contains(term) || album.artists.any { it.name.lowercase().contains(term) }
    } }
    val collator = Collator.getInstance(Locale.CHINA)
    val sorted = when (layout.sort) {
        AlbumPageSort.NAME -> filtered.sortedWith { a, b -> collator.compare(a.name, b.name) }
        AlbumPageSort.RECENTLY_COLLECTED -> filtered.sortedByDescending { it.subTime }
    }
    // Group by the complete album credit, never duplicate a compilation for each track artist.
    return if (layout.groupByArtist) sorted.sortedWith { a, b -> collator.compare(a.artistLabel(), b.artistLabel()) } else sorted
}
