package com.lin0721.linmusic.feature.personallibrary

import kotlinx.serialization.Serializable

enum class LibraryPage(val key: String, val sections: List<Pair<String, String>>) {
    PERSONAL("personal", listOf("playlists" to "歌单", "artists" to "歌手", "albums" to "专辑",
        "local_recent" to "本地最近添加", "local_playlists" to "本地歌单", "local_artists" to "本地歌手", "local_albums" to "本地专辑")),
    LOCAL("local", listOf("local_playlists" to "本地歌单", "recent" to "最近添加", "artists" to "歌手", "albums" to "专辑"))
}

@Serializable
data class LibrarySectionLayout(val key: String, val visible: Boolean = true)

@Serializable
data class LibraryPageLayout(
    val version: Int = 1,
    val sections: List<LibrarySectionLayout> = emptyList(),
    val playlistMode: String = "list",
    val listColumns: Int = 1,
    val coverColumns: Int = 3,
    val itemLimits: Map<String, Int> = emptyMap(),
    val itemOrders: Map<String, List<String>> = emptyMap(),
    // null preserves beta.23's default of showing every playlist. Explicit choices form an allow-list.
    val visiblePlaylistIds: Set<String>? = null
) {
    fun normalized(page: LibraryPage): LibraryPageLayout {
        val known = page.sections.map { it.first }
        val retained = sections.filter { it.key in known }.distinctBy { it.key }
        return copy(sections = retained + known.filter { key -> retained.none { it.key == key } }.map { key ->
            LibrarySectionLayout(key, visible = page != LibraryPage.PERSONAL || !key.startsWith("local_"))
        },
            playlistMode = if (playlistMode == "covers") "covers" else "list",
            listColumns = listColumns.coerceIn(1, 5), coverColumns = coverColumns.coerceIn(1, 5),
            itemLimits = itemLimits.filterKeys { it in known && supportsItemLimit(it) }.mapValues { it.value.coerceIn(0, 5000) },
            itemOrders = itemOrders.filterKeys { it in known && supportsItemOrder(it) }.mapValues { (_, ids) -> ids.filter { it.isNotBlank() }.distinct() },
            visiblePlaylistIds = visiblePlaylistIds?.filterTo(linkedSetOf()) { it.isNotBlank() })
    }

    fun moved(page: LibraryPage, key: String, direction: Int): LibraryPageLayout {
        val current = normalized(page)
        val index = current.sections.indexOfFirst { it.key == key }
        if (index < 0) return current
        val target = (index + direction.coerceIn(-1, 1)).coerceIn(current.sections.indices)
        val reordered = current.sections.toMutableList()
        val item = reordered.removeAt(index)
        reordered.add(target, item)
        return current.copy(sections = reordered)
    }

    val playlistColumns: Int get() = if (playlistMode == "covers") coverColumns else listColumns

    fun playlistIsVisible(id: String): Boolean = visiblePlaylistIds?.contains(id) ?: true

    fun setPlaylistVisible(id: String, show: Boolean, availableIds: Set<String>): LibraryPageLayout {
        val selected = visiblePlaylistIds ?: availableIds
        return copy(visiblePlaylistIds = if (show) selected + id else selected - id)
    }

    fun itemLimit(key: String): Int = itemLimits[key]?.coerceIn(0, 5000) ?: 10

    /** Stable identities are display-only; missing records stay in the saved order for later scans. */
    fun <T> orderedItems(key: String, records: List<T>, id: (T) -> String): List<T> {
        val positions = itemOrders[key].orEmpty().withIndex().associate { it.value to it.index }
        return if (positions.isEmpty()) records else records.sortedBy { positions[id(it)] ?: Int.MAX_VALUE }
    }

    fun <T> shelfItems(key: String, records: List<T>, id: (T) -> String): List<T> {
        val ordered = orderedItems(key, records, id)
        return if (itemLimit(key) == 0) ordered else ordered.take(itemLimit(key))
    }

    fun moveItem(key: String, id: String, direction: Int, availableIds: List<String>): LibraryPageLayout {
        val visible = orderedItems(key, availableIds.distinct()) { it }.toMutableList()
        val index = visible.indexOf(id)
        if (index < 0) return this
        val target = if (direction == 0) 0 else (index + direction.coerceIn(-1, 1)).coerceIn(visible.indices)
        visible.removeAt(index); visible.add(target, id)
        val missing = itemOrders[key].orEmpty().filterNot { it in availableIds }
        return copy(itemOrders = itemOrders + (key to (visible + missing)))
    }
}

internal fun supportsItemOrder(key: String) = key != "recent" && key != "local_recent"
internal fun supportsItemLimit(key: String) = supportsItemOrder(key) && key != "playlists"

internal fun localSectionKey(personalKey: String): String = when (personalKey) {
    "local_artists" -> "artists"
    "local_albums" -> "albums"
    else -> personalKey
}

/** Both entry points show the same local ordering. Page section visibility/order remains independent. */
internal fun LibraryPageLayout.withLocalShelfSettings(local: LibraryPageLayout): LibraryPageLayout {
    val mapping = listOf("local_playlists", "local_artists", "local_albums")
    return copy(itemLimits = itemLimits + mapping.associateWith { local.itemLimit(localSectionKey(it)) },
        itemOrders = itemOrders + mapping.associateWith { local.itemOrders[localSectionKey(it)].orEmpty() })
}
