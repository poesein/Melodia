package com.lin0721.linmusic.core.source

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

// Strict mode verifies song identity metadata. It does not claim to compare the audio recording.
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
        // Platforms may list the same track/album under several IDs. Prefer the closest known
        // duration after all required identity fields pass, rather than rejecting every edition.
        return accepted.minByOrNull {
            if (targetDurationMs > 0L && it.durationMs > 0L) abs(targetDurationMs - it.durationMs) else Long.MAX_VALUE
        }
    }

    fun matches(
        candidate: ExternalTrack,
        targetName: String,
        targetArtists: List<String>,
        targetAlbum: String?,
        targetDurationMs: Long
    ): Boolean {
        val name = recordingText(targetName)
        if (name.isEmpty() || name != recordingText(candidate.name)) return false
        val expectedArtists = targetArtists.map(::identityText).filter(String::isNotEmpty).toSet()
        val candidateArtists = candidate.artistList.takeIf { it.isNotEmpty() }
            ?: if (expectedArtists.size == 1 && identityText(candidate.artists) == expectedArtists.single())
                listOf(candidate.artists)
            else candidate.artists.split(Regex("[、&;，,/|·・]+"))
        val actualArtists = candidateArtists.map(::identityText).filter(String::isNotEmpty).toSet()
        if (expectedArtists.isEmpty() || actualArtists != expectedArtists) return false
        if (candidate.id.isBlank() && candidate.hash.isNullOrBlank()) return false
        val album = recordingText(targetAlbum.orEmpty())
        if (album.isEmpty() || album != recordingText(candidate.albumName)) return false
        // 容许平台按秒取整；超过约 1% 或 3 秒的差异均不能通过。
        val tolerance = minOf(3_000L, maxOf(500L, targetDurationMs / 100))
        if (targetDurationMs > 0L && candidate.durationMs > 0L && abs(targetDurationMs - candidate.durationMs) > tolerance) return false

        // 精选集/再版的中性专辑名差异不证明换了录音；明确的 Live/伴奏/语言等版本冲突必须拒绝。
        return versionSignature(targetName, targetAlbum) == versionSignature(candidate.name, candidate.albumName)
    }

    private fun identityText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace(Regex("[\\p{P}\\p{Z}\\s]+"), "")

    private fun recordingText(value: String): String {
        var text = Normalizer.normalize(value, Normalizer.Form.NFKC)
        fun editionLabel(label: String): Boolean {
            val words = Regex("(?i)remaster(?:ed|ing)?|reissue|deluxe|anniversary|expanded|bonus|edition|version|digital|special|super|legacy|collector'?s|limited|standard|audio|mono|stereo|重制版?|重製版?|再版|豪华版?|豪華版?|周年纪念版?|周年紀念版?|纪念版|紀念版|扩展版|擴展版|典藏版|珍藏版|数字版|數字版")
            if (!words.containsMatchIn(label)) return false
            return words.replace(label, "").replace(Regex("(?i)\\d+(?:st|nd|rd|th)?|[\\p{P}\\p{Z}\\s]+|年"), "").isEmpty()
        }
        text = Regex("\\([^()]*\\)|\\[[^\\[\\]]*\\]").replace(text) { group ->
            if (editionLabel(group.value.drop(1).dropLast(1))) "" else group.value
        }
        val suffix = Regex("\\s+[-–—:]\\s+([^\\n]+)$").find(text)
        if (suffix != null && editionLabel(suffix.groupValues[1])) text = text.substring(0, suffix.range.first)
        return identityText(text)
    }

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
            "cantonese" to "粤语版|粵語版|(?i)cantonese",
            "mandarin" to "国语版|國語版|普通话版|普通話版|(?i)mandarin",
            "english" to "英文版|英语版|英語版|(?i)english version",
            "japanese" to "日语版|日語版|日文版|(?i)japanese version"
        )
        return markers.filterValues { Regex(it).containsMatchIn(text) }.keys
    }
}
