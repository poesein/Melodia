package com.lin0721.linmusic.feature.localmusic.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class LocalAudioCandidate(
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long
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

    fun sameMetadata(title: String, artist: String, candidate: LocalAudioCandidate): Boolean {
        val names = artists(artist)
        return sameTitle(title, candidate.title) &&
            names.isNotEmpty() && names == artists(candidate.artist)
    }

    fun uniqueMatch(title: String, artist: String, durationMs: Long, candidates: List<LocalAudioCandidate>): String? {
        if (durationMs <= 0L) return null
        return candidates.filter {
            sameMetadata(title, artist, it) && it.durationMs > 0L && abs(it.durationMs - durationMs) <= 2_000L
        }.distinctBy { it.uri }.singleOrNull()?.uri
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
