package com.lin0721.linmusic.core.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictSourceCandidatePolicyTest {
    private fun track(id: String = "q1", name: String = "龙拳", artists: List<String> = listOf("周杰伦"),
                      album: String = "八度空间", duration: Long = 274_000) = ExternalTrack(
        id = id, name = name, artists = artists.joinToString(" / "), artistList = artists,
        albumName = album, durationMs = duration, platform = MusicPlatform.QQ
    )

    private fun match(candidate: ExternalTrack, name: String = "龙拳", artists: List<String> = listOf("周杰伦"),
                      album: String? = "八度空间", duration: Long = 274_000) =
        StrictSourceCandidatePolicy.matches(candidate, name, artists, album, duration)

    private fun pick(candidates: List<ExternalTrack>) = StrictSourceCandidatePolicy.pickBestCandidate(
        candidates, "龙拳", listOf("周杰伦"), "八度空间", 274_000
    )

    @Test fun `完整精确候选可进入实际音频核验阶段`() { assertTrue(match(track())) }
    @Test fun `只做标点宽度空白规范化不接受包含歌名`() {
        assertTrue(match(track(name = " Ａ－Ｂ "), name = "a-b"))
        assertFalse(match(track(name = "龙拳串烧")))
        assertFalse(match(track(name = "01 龙拳")))
    }
    @Test fun `歌名和歌手缺失都不能通过`() {
        assertFalse(match(track(name = "")))
        assertFalse(match(track(artists = emptyList())))
        assertFalse(match(track(), name = ""))
        assertFalse(match(track(), artists = emptyList()))
    }
    @Test fun `歌手必须完整集合一致而非部分或子串命中`() {
        assertFalse(match(track(artists = listOf("周杰伦", "合作者"))))
        assertFalse(match(track(artists = listOf("周杰伦音乐馆"))))
        assertTrue(match(track(artists = listOf("乙", "甲")), artists = listOf("甲", "乙")))
    }
    @Test fun `缺时长不把零或未知当作接近`() {
        assertFalse(match(track(duration = 0)))
        assertFalse(match(track(), duration = 0))
    }
    @Test fun `时长容差最多百分之一并封顶三秒`() {
        assertTrue(match(track(duration = 272_000)))
        assertFalse(match(track(duration = 270_000)))
        assertFalse(match(track(duration = 304_000), duration = 300_000))
        assertFalse(match(track(duration = 61_000), duration = 60_000))
    }
    @Test fun `明示现场与录音室版不能互相替换`() {
        assertFalse(match(track(name = "龙拳 (Live)")))
        assertFalse(match(track(album = "周杰伦演唱会")))
        assertTrue(match(track(name = "龙拳（Live）", album = "Live"), name = "龙拳 (live)", album = "演唱会"))
    }
    @Test fun `专辑标题中的伴奏重录混音等冲突不能被忽略`() {
        listOf("Instrumental", "Remix", "Acoustic", "Demo", "Cover", "重录版", "Remastered", "粤语版").forEach {
            assertFalse(it, match(track(album = it)))
        }
    }
    @Test fun `中性精选集或专辑名差异本身不替代音频核验也不误作版本冲突`() {
        assertTrue(match(track(album = "精选集")))
        assertTrue(match(track(album = "")))
        assertTrue(match(track(), album = null))
    }
    @Test fun `同名同歌手同专辑的不同标识仍按歧义拒绝`() {
        assertNull(pick(listOf(track("q1"), track("q2"))))
    }
    @Test fun `重复同标识搜索行不制造歧义`() {
        assertEquals("q1", pick(listOf(track("q1"), track("q1")))?.id)
    }
    @Test fun `筛掉错误条目后唯一正确候选可通过`() {
        assertEquals("right", pick(listOf(track("wrong", name = "龙拳 (Live)"), track("right")))?.id)
    }
    @Test fun `没有标识的条目不能用于请求`() { assertFalse(match(track(id = ""))) }
    @Test fun `结构化乐队名称保留斜杠不被误拆`() {
        assertTrue(match(track(artists = listOf("AC/DC")), artists = listOf("AC/DC")))
    }
}
