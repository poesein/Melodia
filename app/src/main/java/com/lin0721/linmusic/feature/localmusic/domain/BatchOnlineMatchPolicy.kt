package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Track
import java.text.Normalizer
import java.util.Locale

enum class BatchOnlineMatchStatus { MATCHED, NO_MATCH, NEEDS_CONFIRMATION }

data class BatchOnlineMatchDecision(
    val status: BatchOnlineMatchStatus,
    val matchedTrack: Track? = null,
    val candidates: List<Track> = emptyList(),
    val reason: String
)

/** The result is evidence from supplied search results, not a guarantee about the whole catalog. */
object BatchOnlineMatchPolicy {
    private val unknownAlbums = setOf("", "<unknown>", "unknown", "unknown album", "未知专辑")

    fun evaluate(local: LocalAudioCandidate, candidates: List<Track>): BatchOnlineMatchDecision {
        fun noMatch(reason: String, nearby: List<Track> = emptyList()) =
            BatchOnlineMatchDecision(BatchOnlineMatchStatus.NO_MATCH, candidates = nearby, reason = reason)

        if (local.title.isBlank()) return noMatch("本地歌曲缺少歌名，请先补充标签或手动关联")
        if (!PreferredLocalAudioMatcher.hasKnownArtist(local.artist)) return noMatch("本地歌曲缺少完整歌手标签，请手动关联")
        if (!knownAlbum(local.album)) return noMatch("本地歌曲缺少专辑标签，无法自动确认版本")
        if (local.durationMs <= 0L) return noMatch("本地歌曲缺少有效时长，无法自动确认版本")
        if (PreferredLocalAudioMatcher.hiddenVersion(local)) return noMatch("文件名含有标签未注明的版本信息，请手动确认")

        val valid = candidates.filter { it.id > 0L }.distinctBy { it.id }
        if (valid.isEmpty()) return noMatch("网易云搜索没有返回歌曲")
        val titleMatches = valid.filter { PreferredLocalAudioMatcher.sameLocalTitle(it.name, local.title) }
        if (titleMatches.isEmpty()) return noMatch("搜索结果中没有完整歌名和版本相同的歌曲", valid)
        val metadataComplete = titleMatches.filter {
            it.ar.isNotEmpty() && it.ar.all { artist -> PreferredLocalAudioMatcher.hasKnownArtist(artist.name) } &&
                knownAlbum(it.al.name) && it.dt > 0L
        }
        if (metadataComplete.isEmpty()) return noMatch("候选歌曲的歌手、专辑或时长信息不完整，请手动确认", titleMatches)
        val artistMatches = metadataComplete.filter {
            PreferredLocalAudioMatcher.sameMetadata(it.name, it.ar.joinToString(" • ") { artist -> artist.name }, local, it.al.name)
        }
        if (artistMatches.isEmpty()) return noMatch("候选歌曲的完整歌手署名与本地标签不一致", titleMatches)
        val albumMatches = artistMatches.filter { PreferredLocalAudioMatcher.sameAlbum(local.album, it.al.name) }
        if (albumMatches.isEmpty()) return noMatch("候选歌曲的专辑与本地标签不一致，请确认录音版本", artistMatches)
        val strong = albumMatches.filter { PreferredLocalAudioMatcher.matchingDuration(it.dt, local, it.al.name) }
        if (strong.isEmpty()) return noMatch("候选歌曲的时长差超过允许范围，请手动确认", albumMatches)
        if (strong.size > 1) return BatchOnlineMatchDecision(
            BatchOnlineMatchStatus.NEEDS_CONFIRMATION,
            candidates = strong,
            reason = "多首网易云歌曲具有相同歌名、歌手、专辑和相近时长，请选择歌曲 ID"
        )
        val matched = strong.single()
        return BatchOnlineMatchDecision(
            BatchOnlineMatchStatus.MATCHED,
            matchedTrack = matched,
            candidates = listOf(matched),
            reason = "歌名、完整歌手、专辑和时长均符合自动匹配规则"
        )
    }

    private fun knownAlbum(value: String?): Boolean = value != null &&
        Normalizer.normalize(value, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT) !in unknownAlbums
}
