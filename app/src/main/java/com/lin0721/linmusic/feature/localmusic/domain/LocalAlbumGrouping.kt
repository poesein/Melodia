package com.lin0721.linmusic.feature.localmusic.domain

import java.security.MessageDigest

/** Read-only grouping evidence. Inferred album ownership never changes a file's artist tags. */
internal data class LocalAlbumGroupMetadata(
    val album: String?,
    val artist: String,
    val albumArtist: String?,
    val folderPath: String?
)

internal data class LocalAlbumGroupIdentity(val key: String, val name: String, val owner: String)

internal fun localAlbumOwner(artist: String, albumArtist: String?): String =
    splitArtists(albumArtist?.takeIf(String::isNotBlank) ?: artist).first()

/**
 * Preserve the existing title/album-artist keys except for an unlabelled compilation in its own
 * album directory. A shared title alone is insufficient: flat libraries can contain unrelated
 * artists' albums named Greatest Hits. Folder identity also keeps separate copies independent.
 */
internal fun resolveLocalAlbumGroups(entries: List<LocalAlbumGroupMetadata>): List<LocalAlbumGroupIdentity> {
    val resolved = entries.map { entry ->
        val title = albumTitleOf(entry.album, entry.folderPath)
        val owner = localAlbumOwner(entry.artist, entry.albumArtist)
        LocalAlbumGroupIdentity(title?.let { "$it|$owner" } ?: UNKNOWN_ALBUM_KEY,
            title ?: UNKNOWN_ALBUM_NAME, owner)
    }.toMutableList()

    entries.indices.groupBy { entries[it].folderPath }.forEach { (folder, indices) ->
        if (folder == null || indices.size < 2 || indices.any { !entries[it].albumArtist.isNullOrBlank() }) return@forEach
        val titles = indices.map { albumTitleOf(entries[it].album, folder) }.distinct()
        val title = titles.singleOrNull() ?: return@forEach
        if (title != folder.substringAfterLast('/')) return@forEach
        val artists = indices.map { localAlbumOwner(entries[it].artist, null) }
            .filterNot { it == UNKNOWN_ARTIST_NAME || it == "<unknown>" }.distinct()
        if (artists.size < 2) return@forEach

        val identity = LocalAlbumGroupIdentity(compilationKey(folder, title), title, "Various Artists")
        indices.forEach { resolved[it] = identity }
    }
    return resolved
}

private fun compilationKey(folder: String, title: String): String {
    // Length prefixes make the two inputs unambiguous, including folders or titles containing '|'.
    val evidence = "${folder.length}:$folder${title.length}:$title".toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(evidence)
    return "compilation:" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
