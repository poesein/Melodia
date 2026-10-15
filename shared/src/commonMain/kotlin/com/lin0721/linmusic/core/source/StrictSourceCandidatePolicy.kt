package com.lin0721.linmusic.core.source

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

// 只筛选待核验候选，不证明候选 URL 的实际音频；后者必须由 StrictAudioVerifier 独立检查。
object StrictSourceCandidatePolicy {
    fun pickBestCandidate(
        candidates: List<ExternalTrack>,
        targetName: String,
        targetArtists: List<String>,
        targetAlbum: String?,
        targetDurationMs: Long
    ): ExternalTrack? {
        val accepted = candidates.filter {
            matches(it, targetName, targetArtists, targetAlbum, targetDurationMs)
        }.distinctBy { "${it.platform.key}:${it.hash?.takeIf(String::isNotBlank) ?: it.id}" }
        // 多个不同平台标识均通过时不能靠列表顺序猜；同一标识的重复搜索行不算歧义。
        return accepted.singleOrNull()
    }

    fun matches(
        candidate: ExternalTrack,
        targetName: String,
        targetArtists: List<String>,
        targetAlbum: String?,
        targetDurationMs: Long
    ): Boolean {
        val name = identityText(targetName)
        if (name.isEmpty() || name != identityText(candidate.name)) return false
        val expectedArtists = targetArtists.map(::identityText).filter(String::isNotEmpty).toSet()
        val candidateArtists = candidate.artistList.takeIf { it.isNotEmpty() }
            ?: if (expectedArtists.size == 1 && identityText(candidate.artists) == expectedArtists.single())
                listOf(candidate.artists)
            else candidate.artists.split(Regex("[、&;，,/|·・]+"))
        val actualArtists = candidateArtists.map(::identityText).filter(String::isNotEmpty).toSet()
        if (expectedArtists.isEmpty() || actualArtists != expectedArtists) return false
        if (candidate.id.isBlank() && candidate.hash.isNullOrBlank()) return false
        if (targetDurationMs <= 0 || candidate.durationMs <= 0) return false
        // 容许平台按秒取整；超过约 1% 或 3 秒的差异均不能通过。
        val tolerance = minOf(3_000L, maxOf(500L, targetDurationMs / 100))
        if (abs(targetDurationMs - candidate.durationMs) > tolerance) return false

        // 精选集/再版的中性专辑名差异不证明换了录音；明确的 Live/伴奏/语言等版本冲突必须拒绝。
        return versionSignature(targetName, targetAlbum) == versionSignature(candidate.name, candidate.albumName)
    }

    private fun identityText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace(Regex("[\\p{P}\\p{Z}\\s]+"), "")

    private fun versionSignature(title: String, album: String?): Set<String> {
        val text = "$title ${album.orEmpty()}"
        val markers = linkedMapOf(
            "live" to "(?i)(?:^|[^a-z])live(?:$|[^a-z])|现场|現場|演唱会|演唱會",
            "instrumental" to "(?i)instrumental|伴奏|纯音乐|純音樂",
            "karaoke" to "(?i)karaoke|卡拉",
            "remix" to "(?i)remix|混音",
            "acoustic" to "(?i)acoustic|不插电|不插電",
            "demo" to "(?i)(?:^|[^a-z])demo(?:$|[^a-z])|小样|小樣",
            "cover" to "(?i)(?:^|[^a-z])cover(?:$|[^a-z])|翻唱",
            "rerecorded" to "(?i)re[ -]?record|重录|重錄|重新录制|重新錄製",
            "remaster" to "(?i)remaster|重制|重製",
            "cantonese" to "粤语版|粵語版|(?i)cantonese",
            "mandarin" to "国语版|國語版|普通话版|普通話版|(?i)mandarin",
            "english" to "英文版|英语版|英語版|(?i)english version",
            "japanese" to "日语版|日語版|日文版|(?i)japanese version"
        )
        return markers.filterValues { Regex(it).containsMatchIn(text) }.keys
    }
}
