package com.lin0721.linmusic.feature.localmusic.domain

/** A legacy file may have several online IDs; never choose one by map iteration order. */
internal object ManualLocalAudioAssociations {
    fun uniqueByIdentity(
        links: Map<Long, String>,
        identityOf: (String) -> String?
    ): Map<String, Long> = links.entries
        .filter { it.key > 0L }
        .mapNotNull { (songId, uri) -> identityOf(uri)?.let { it to songId } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (identity, ids) -> ids.distinct().singleOrNull()?.let { identity to it } }
        .toMap()

    fun idsForIdentity(
        links: Map<Long, String>,
        identity: String,
        identityOf: (String) -> String?
    ): Set<Long> = links.filterValues { identityOf(it) == identity }.keys
}
