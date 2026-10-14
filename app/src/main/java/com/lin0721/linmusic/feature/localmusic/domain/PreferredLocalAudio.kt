package com.lin0721.linmusic.feature.localmusic.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class LocalAudioCandidate(
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    // MediaStore and SAF can refer to the same physical audio file.
    val identity: String? = null,
    val album: String? = null,
    val fileName: String? = null
)

/** Matching deliberately keeps version suffixes, punctuation and every artist. */
object PreferredLocalAudioMatcher {
    private val whitespace = Regex("\\s+")
    private val artistSeparator = Regex("\\s*(?:[,，、;；•]|\\s+/\\s+)\\s*")
    private val unknownArtists = setOf("", "<unknown>", "unknown", "未知艺术家", "未知歌手")

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(whitespace, " ")

    private fun artists(value: String): Set<String> = value.split(artistSeparator)
        .map(::normalize).filter { it !in unknownArtists }.toSet()

    fun sameTitle(title: String, other: String): Boolean = normalize(title).isNotEmpty() && normalize(title) == normalize(other)

    private fun sameAlbum(album: String?, other: String?): Boolean =
        !album.isNullOrBlank() && !other.isNullOrBlank() && normalize(album) == normalize(other)

    fun sameMetadata(title: String, artist: String, candidate: LocalAudioCandidate, onlineAlbum: String? = null): Boolean {
        val names = artists(artist)
        if (!sameTitle(title, candidate.title) || names.isEmpty() || hiddenVersion(candidate)) return false
        val localNames = artists(candidate.artist)
        if (names == localNames) return true
        // Some real tags serialize a verified multi-artist credit as A/B. A single
        // online artist such as AC/DC remains a whole name; require matching album.
        return names.size > 1 && names.none { '/' in it } && sameAlbum(onlineAlbum, candidate.album) &&
            localNames.flatMap { it.split('/') }.map(::normalize).filter { it !in unknownArtists }.toSet() == names
    }

    private fun hiddenVersion(candidate: LocalAudioCandidate): Boolean {
        val fileName = candidate.fileName?.substringBeforeLast('.') ?: return false
        val nameWithoutArtist = fileName.replace(Regex("^${Regex.escape(candidate.artist)}\\s*[-_—]\\s*", RegexOption.IGNORE_CASE), "")
        val markers = Regex("(?i)\\b(live|remaster(?:ed)?|acoustic|instrumental|karaoke|remix|demo)\\b|现场|重制|重录|伴奏|不插电")
        fun hints(value: String) = markers.findAll(value).map { normalize(it.value) }.toSet()
        return hints(nameWithoutArtist).any { it !in hints(candidate.title) }
    }

    private fun formatRank(candidate: LocalAudioCandidate): Int = when (candidate.fileName?.substringAfterLast('.')?.lowercase(Locale.ROOT)) {
        "flac", "ape" -> 4
        "wav", "aiff", "aif" -> 3
        "m4a", "alac" -> 2
        else -> 1
    }

    fun uniqueMatch(title: String, artist: String, durationMs: Long, candidates: List<LocalAudioCandidate>, onlineAlbum: String? = null): String? {
        if (durationMs <= 0L) return null
        val matches = candidates.filter {
            val tolerance = if (sameAlbum(onlineAlbum, it.album)) (durationMs / 50L).coerceIn(2_000L, 5_000L) else 2_000L
            sameMetadata(title, artist, it, onlineAlbum) && it.durationMs > 0L && abs(it.durationMs - durationMs) <= tolerance
        }.distinctBy { it.identity ?: it.uri }
        if (matches.size == 1) return matches.single().uri
        if (matches.isEmpty() || matches.any { !sameAlbum(onlineAlbum, it.album) }) return null
        if (matches.maxOf { it.durationMs } - matches.minOf { it.durationMs } > 2_000L) return null
        // Equivalent copies of the confirmed album/version may use different encodings.
        return matches.sortedWith(compareByDescending<LocalAudioCandidate> { formatRank(it) }.thenBy { it.uri }).first().uri
    }
}

/** An explicit choice wins, even when unavailable: never silently substitute another local version. */
suspend fun resolvePreferredLocalAudio(
    enabled: Boolean,
    manualUri: String?,
    automaticEnabled: Boolean,
    automaticUri: suspend () -> String?,
    isReadable: suspend (String) -> Boolean
): String? {
    if (!enabled) return null
    if (manualUri != null) return manualUri.takeIf { isReadable(it) }
    if (!automaticEnabled) return null
    val uri = automaticUri() ?: return null
    return uri.takeIf { isReadable(it) }
}
