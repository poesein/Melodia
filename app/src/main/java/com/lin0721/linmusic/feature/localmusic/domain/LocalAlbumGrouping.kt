package com.lin0721.linmusic.feature.localmusic.domain

import java.security.MessageDigest
import java.util.Locale

/** Read-only grouping evidence. Inferred album ownership never changes a file's artist tags. */
internal data class LocalAlbumGroupMetadata(
    val album: String?,
    val artist: String,
    val albumArtist: String?,
    val folderPath: String?
)

internal data class LocalAlbumGroupIdentity(val key: String, val name: String, val owner: String)

internal fun localAlbumOwner(artist: String, albumArtist: String?): String =
    splitArtists(knownAlbumArtist(albumArtist) ?: artist).first()

private fun knownAlbumArtist(value: String?): String? = value?.trim()?.takeIf {
    it.isNotEmpty() && it != UNKNOWN_ARTIST_NAME && !it.equals("<unknown>", ignoreCase = true)
}

private fun artistNames(value: String): Set<String> =
    splitArtists(value).mapTo(HashSet()) { it.lowercase(Locale.ROOT) }

// Some Android extractors repeat each track's performer in ALBUM_ARTIST. That adds no album-level
// ownership evidence. A genuinely different album artist still takes priority over folder inference.
private fun LocalAlbumGroupMetadata.hasDistinctAlbumArtist(): Boolean = knownAlbumArtist(albumArtist)?.let {
    artistNames(it) != artistNames(artist) && it.lowercase(Locale.ROOT) !in compilationArtists
} ?: false

private val compilationArtists = setOf("various artists", "various", "v.a.", "va", "群星", "多位歌手")
private val sharedLibraryFolders = setOf("music", "audio", "audios", "songs", "download", "downloads",
    "media", "library", "音乐", "歌曲", "曲库", "下载")

private fun hasAlbumDirectoryEvidence(directory: String, title: String): Boolean {
    fun normalized(value: String) = value.replace(Regex("""\[[^\]]*]"""), "")
        .filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT)
    val folderName = normalized(directory)
    val albumName = normalized(title)
    return albumName.isNotEmpty() && (folderName == albumName ||
        (albumName.length >= 4 && folderName.contains(albumName)) ||
        (folderName.length >= 8 && albumName.startsWith(folderName)))
}

/**
 * Preserve the existing title/album-artist keys except for compilations with directory evidence.
 * A shared title alone is insufficient: flat libraries can contain unrelated artists' albums
 * named Greatest Hits. The directory need not have the exact tag spelling (e.g. a renamed rip).
 * Inference is read-only, so individual performer tags and existing song associations stay intact.
 */
internal fun resolveLocalAlbumGroups(entries: List<LocalAlbumGroupMetadata>): List<LocalAlbumGroupIdentity> {
    val resolved = entries.map { entry ->
        val title = albumTitleOf(entry.album, entry.folderPath)
        val owner = localAlbumOwner(entry.artist, entry.albumArtist)
        LocalAlbumGroupIdentity(title?.let { "$it|$owner" } ?: UNKNOWN_ALBUM_KEY,
            title ?: UNKNOWN_ALBUM_NAME, owner)
    }.toMutableList()

    entries.indices.groupBy { entries[it].folderPath }.forEach { (folder, indices) ->
        if (folder == null || indices.size < 2) return@forEach
        val directoryName = folder.substringAfterLast('/').trim()
        if (directoryName.lowercase(Locale.ROOT) in sharedLibraryFolders) return@forEach
        val byTitle = indices.groupBy { albumTitleOf(entries[it].album, folder) }
        byTitle.forEach album@{ (title, albumIndices) ->
            if (title == null || albumIndices.size < 2) return@album
            // Directory evidence is still required even when all tracks share a title: an Inbox
            // can contain unrelated Greatest Hits albums. Ignore punctuation and rip annotations.
            if (!hasAlbumDirectoryEvidence(directoryName, title)) return@album
            if (albumIndices.any { entries[it].hasDistinctAlbumArtist() }) return@album
            val artists = albumIndices.map { localAlbumOwner(entries[it].artist, null) }
                .filterNot { it == UNKNOWN_ARTIST_NAME || it.equals("<unknown>", ignoreCase = true) }
                .map { it.lowercase(Locale.ROOT) }.distinct()
            if (artists.size < 2) return@album
            // A consistent, explicit compilation artist already has a stable legacy key.
            val explicit = albumIndices.map { knownAlbumArtist(entries[it].albumArtist) }
            if (explicit.all { it != null } && explicit.distinct().size == 1) return@album

            val identity = LocalAlbumGroupIdentity(compilationKey(folder, title), title, "Various Artists")
            albumIndices.forEach { resolved[it] = identity }
        }
    }
    return resolved
}

private fun compilationKey(folder: String, title: String): String {
    // Length prefixes make the two inputs unambiguous, including folders or titles containing '|'.
    val evidence = "${folder.length}:$folder${title.length}:$title".toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(evidence)
    return "compilation:" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
