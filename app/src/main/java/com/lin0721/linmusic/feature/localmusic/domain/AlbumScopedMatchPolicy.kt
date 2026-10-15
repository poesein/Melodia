package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class AlbumScopedLocal(val candidate: LocalAudioCandidate, val trackNumber: Int? = null)

enum class AlbumScopedMatchStatus { MATCH, REVIEW, NO_MATCH }

data class AlbumScopedMatchDecision(
    val localUri: String,
    val status: AlbumScopedMatchStatus,
    val match: Track? = null,
    val candidates: List<Track> = emptyList(),
    val reason: String
)

/**
 * Used only after the user chooses the corresponding online album. Album and artist tags no longer
 * gate a match; recording identity still needs a unique title and compatible, known duration.
 * The caller supplies that album's complete track list, never a global search result or a subset
 * made by removing already-associated tracks. A fresh evaluate() is also required before saving.
 */
object AlbumScopedMatchPolicy {
    private val whitespace = Regex("\\s+")
    private val punctuationAndSpace = Regex("[\\p{P}\\s]+")
    private val unknownTitles = setOf("", "<unknown>", "unknown", "unknown title", "未知歌曲", "未知标题")
    private val recordingMarker = Regex("(?i)\\b(live|acoustic|instrumental|karaoke|remix|demo|re[- ]?record(?:ed|ing))\\b|现场|重录|伴奏|不插电")
    private val bracketSuffix = Regex("\\s*[（(\\[【]([^()（）\\[\\]【】]+)[）)\\]】]\\s*$")
    private val separatedVersion = Regex("\\s+[-–—:]\\s+(.+)$")
    private val bareVersion = Regex("(?i)\\s+((?:live(?:\\s+(?:at|in|from)\\s+.+)?|acoustic|instrumental|karaoke|remix|demo|re[- ]?record(?:ed|ing))(?:\\s+(?:version|recording))?|(?:\\d{4}年?\\s*)?(?:现场|重录|伴奏|不插电)(?:版|版本)?)$")

    fun plan(locals: List<AlbumScopedLocal>, onlineTracks: List<Track>): List<AlbumScopedMatchDecision> {
        val decisions = locals.distinctBy { it.candidate.uri }.map { evaluate(it.candidate, onlineTracks, it.trackNumber) }
        // A preference maps one online song to one local file. Even identical encodings of a song
        // must be chosen explicitly when more than one local row claims that song in this plan.
        val collisions = decisions.filter { it.status == AlbumScopedMatchStatus.MATCH }
            .groupBy { it.match!!.id }.filterValues { it.size > 1 }.keys
        return decisions.map { decision ->
            if (decision.match?.id !in collisions) decision else decision.copy(
                status = AlbumScopedMatchStatus.REVIEW,
                match = null,
                reason = "多首本地歌曲对应同一首在线歌曲，请选择要使用的本地文件"
            )
        }
    }

    fun evaluate(local: LocalAudioCandidate, onlineTracks: List<Track>): AlbumScopedMatchDecision =
        evaluate(local, onlineTracks, null)

    private fun evaluate(local: LocalAudioCandidate, onlineTracks: List<Track>, trackNumber: Int?): AlbumScopedMatchDecision {
        val valid = onlineTracks.filter { it.id > 0L && knownTitle(it.name) }.distinctBy { it.id }
        fun result(status: AlbumScopedMatchStatus, reason: String, candidates: List<Track>, match: Track? = null) =
            AlbumScopedMatchDecision(local.uri, status, match, candidates, reason)
        if (local.uri.isBlank()) return result(AlbumScopedMatchStatus.NO_MATCH, "本地歌曲缺少文件引用", emptyList())
        if (valid.isEmpty()) return result(AlbumScopedMatchStatus.NO_MATCH, "这张在线专辑没有可用于匹配的曲目", emptyList())
        val resolved = LocalMatchMetadata.resolve(local)
        if (!knownTitle(resolved.title)) return result(AlbumScopedMatchStatus.NO_MATCH, "本地歌曲缺少可识别的歌名，请手动选择", valid)
        val indexed = valid.withIndex().associate { it.value.id to it.index }
        fun titles(online: Track): Set<String> = localTitles(local, online)
        fun exact(online: Track): Boolean = titles(online).any { title -> comparable(title) == comparable(online.name) }
        fun sameBase(online: Track): Boolean = titles(online).any { title ->
            comparable(recordingBase(title)) == comparable(recordingBase(online.name))
        }
        val ranked = valid.sortedWith(compareByDescending<Track> { exact(it) }
            .thenByDescending { sameBase(it) }
            .thenBy { if (local.durationMs > 0L && it.dt > 0L) abs(local.durationMs - it.dt) else Long.MAX_VALUE }
            // Only rank recommendations with the order. A disc-local number such as 1001 is not
            // converted to an album-wide index, and order can never make a row automatic.
            .thenBy { if (trackNumber != null && trackNumber in 1..valid.size) abs(indexed.getValue(it.id) + 1 - trackNumber) else Int.MAX_VALUE }
            .thenBy { indexed.getValue(it.id) })
        val titleMatches = ranked.filter(::exact)
        val related = ranked.filter(::sameBase)
        if (titleMatches.isEmpty()) {
            return if (related.isNotEmpty()) result(AlbumScopedMatchStatus.REVIEW, "找到相近歌名，但现场、伴奏或其他版本信息不同，请核对", ranked)
            else result(AlbumScopedMatchStatus.NO_MATCH, "同专辑中未找到同名歌曲，可参考曲号和时长手动选择", ranked)
        }
        if (PreferredLocalAudioMatcher.hiddenVersion(local)) return result(
            AlbumScopedMatchStatus.REVIEW, "文件名还有标签未注明的录音版本，请核对后选择", ranked
        )
        if (local.durationMs <= 0L) return result(AlbumScopedMatchStatus.REVIEW, "歌名相同，但本地歌曲缺少有效时长，请核对", ranked)
        val compatible = titleMatches.filter { it.dt > 0L && abs(it.dt - local.durationMs) <= tolerance(it.dt) }
        if (compatible.isEmpty()) return result(AlbumScopedMatchStatus.REVIEW,
            if (titleMatches.any { it.dt <= 0L }) "歌名相同，但在线曲目缺少有效时长，请核对" else "歌名相同，但时长差超过专辑匹配的允许范围，请核对", ranked)
        if (compatible.size > 1 || titleMatches.any { it.dt <= 0L }) return result(
            AlbumScopedMatchStatus.REVIEW, "专辑中有多个同名候选，无法仅凭歌名和时长确定，请选择", ranked
        )
        val selected = compatible.single()
        val conflictingCopies = onlineTracks.filter { it.id == selected.id }
            .distinctBy { comparable(it.name) to it.dt }
        if (conflictingCopies.size > 1) return result(AlbumScopedMatchStatus.REVIEW, "在线专辑中同一曲目的信息存在冲突，请核对", ranked)
        return result(AlbumScopedMatchStatus.MATCH, "已确认专辑内歌名唯一对应，时长在放宽范围内；不再要求歌手和专辑标签一致", ranked, selected)
    }

    private fun localTitles(local: LocalAudioCandidate, online: Track): Set<String> = buildSet {
        add(local.title)
        add(LocalMatchMetadata.resolve(local).title)
        val onlineCredits = online.ar.map { it.name }.filter { it.isNotBlank() } + online.ar.joinToString(" • ") { it.name }
        onlineCredits.filter { it.isNotBlank() }.forEach { artist ->
            // The confirmed album provides a reliable credit even when the local artist is missing.
            add(LocalMatchMetadata.resolve(local.copy(artist = artist)).title)
        }
    }.flatMap { listOf(it, LocalMatchTitle.forSearch(it)) }.filter(::knownTitle).toSet()

    private fun tolerance(durationMs: Long): Long = (durationMs / 20L).coerceIn(5_000L, 15_000L)

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(whitespace, " ")

    private fun knownTitle(value: String): Boolean = normalize(value) !in unknownTitles

    private fun comparable(value: String): String {
        val normalized = normalize(LocalMatchAlbum.forComparison(value))
        // Punctuation in a numeric title can carry its meaning: 3.14 must not become 314.
        return if (normalized.any { it.isLetter() }) normalized.replace(punctuationAndSpace, "") else normalized
    }

    /** For suggestions only; all removed recording annotations still prevent automatic matching. */
    private fun recordingBase(value: String): String {
        var body = LocalMatchAlbum.forComparison(value)
        while (true) {
            val bracket = bracketSuffix.find(body)
            if (bracket != null && recordingMarker.containsMatchIn(bracket.groupValues[1])) {
                body = body.substring(0, bracket.range.first).trim()
                continue
            }
            val separated = separatedVersion.find(body)?.takeIf { recordingMarker.containsMatchIn(it.groupValues[1]) }
            val bare = bareVersion.find(body)
            val suffix = separated ?: bare ?: return body
            return body.substring(0, suffix.range.first).trim()
        }
    }
}
