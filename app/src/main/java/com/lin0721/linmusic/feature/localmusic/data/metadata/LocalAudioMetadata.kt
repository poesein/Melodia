package com.lin0721.linmusic.feature.localmusic.data.metadata

import java.util.Locale

/** Text tags only; playback duration remains the Android decoder's responsibility. */
internal data class LocalAudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null
)

internal fun isWaveAudio(fileName: String?, mimeType: String?): Boolean =
    fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in setOf("wav", "wave") ||
        mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) in
        setOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave")

internal fun cleanAudioTag(value: String?): String? = value?.trim()?.takeIf {
    // Unknown is also a legitimate song/album title. Only Android's explicit sentinel is absent.
    it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true)
}

internal fun localAudioMetadataFromProperties(properties: Map<String, Array<String>>): LocalAudioMetadata {
    fun text(key: String): String? = properties[key]?.mapNotNull(::cleanAudioTag)
        ?.takeIf { it.isNotEmpty() }?.joinToString(" / ")
    fun first(key: String): String? = properties[key]?.firstNotNullOfOrNull(::cleanAudioTag)
    val date = first("DATE") ?: first("YEAR")
    return LocalAudioMetadata(
        title = text("TITLE"), artist = text("ARTIST"), album = text("ALBUM"),
        albumArtist = text("ALBUMARTIST"),
        year = date?.take(4)?.toIntOrNull()?.takeIf { it > 0 },
        trackNumber = localTrackNumberFromTags(first("TRACKNUMBER"), first("DISCNUMBER"))
    )
}

internal fun localTrackNumberFromTags(track: String?, disc: String?): Int? {
    val trackNo = track?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it in 1..999 } ?: return null
    val discNo = disc?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 0
    return (discNo.toLong() * 1000L + trackNo).takeIf { it <= Int.MAX_VALUE }?.toInt()
}

internal fun needsEmbeddedAudioTags(system: LocalAudioMetadata, wave: Boolean, extendedFields: Boolean = true): Boolean =
    wave || listOf(system.title, system.artist, system.album).any { cleanAudioTag(it) == null } ||
        (extendedFields && (cleanAudioTag(system.albumArtist) == null || system.year == null || system.trackNumber == null))

/** WAV system extractors can substitute filenames for genuine ID3 tags, so prefer its embedded tags. */
internal fun mergeLocalAudioMetadata(
    system: LocalAudioMetadata, embedded: LocalAudioMetadata?, preferEmbedded: Boolean
): LocalAudioMetadata {
    fun text(systemValue: String?, embeddedValue: String?): String? = if (preferEmbedded)
        cleanAudioTag(embeddedValue) ?: cleanAudioTag(systemValue)
    else cleanAudioTag(systemValue) ?: cleanAudioTag(embeddedValue)
    fun number(systemValue: Int?, embeddedValue: Int?): Int? = if (preferEmbedded)
        embeddedValue?.takeIf { it > 0 } ?: systemValue?.takeIf { it > 0 }
    else systemValue?.takeIf { it > 0 } ?: embeddedValue?.takeIf { it > 0 }
    return LocalAudioMetadata(text(system.title, embedded?.title), text(system.artist, embedded?.artist),
        text(system.album, embedded?.album), text(system.albumArtist, embedded?.albumArtist),
        number(system.year, embedded?.year), number(system.trackNumber, embedded?.trackNumber))
}
