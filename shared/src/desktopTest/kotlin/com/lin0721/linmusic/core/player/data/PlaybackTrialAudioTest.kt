package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.download.data.SongDownloadUrlItem
import com.lin0721.linmusic.core.download.data.isTrialAudio
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTrialAudioTest {

    @Test fun `官方来源显式标记且试听秒起点正确转换为毫秒`() {
        val info = SongUrlItem(url = "https://music.126.net/preview.mp3", time = 30_000,
            freeTrialInfo = FreeTrialInfo(start = 30, end = 60)).toPlaybackInfo()
        assertEquals(PlaybackSourceKind.OFFICIAL, info.sourceKind)
        assertEquals(30_000L, info.referenceStartMs)
        assertTrue(info.isFreeTrial)
        assertFalse(info.strictVerified)
    }

    @Test fun `未知或非法试听起点不猜零也不发生整数溢出`() {
        assertNull(SongUrlItem().toPlaybackInfo().referenceStartMs)
        listOf(FreeTrialInfo(), FreeTrialInfo(-1, 30), FreeTrialInfo(30, 10),
            FreeTrialInfo(Long.MAX_VALUE - 1, Long.MAX_VALUE)).forEach {
            assertNull(SongUrlItem(freeTrialInfo = it).toPlaybackInfo().referenceStartMs)
        }
        assertEquals(0L, SongUrlItem(freeTrialInfo = FreeTrialInfo(0, 30)).toPlaybackInfo().referenceStartMs)
    }

    @Test fun `未注入来源的旧结果默认未知而非官方或已验证`() {
        val info = SongPlaybackInfo("https://unknown.invalid/song.mp3")
        assertEquals(PlaybackSourceKind.UNKNOWN, info.sourceKind)
        assertFalse(info.strictVerified)
        assertNull(info.cacheIdentity)
    }

    @Test
    fun regularSongWithValidDurationIsNotTrial() {
        val item = SongUrlItem(
            id = 12345,
            url = "https://music.126.net/song.mp3",
            time = 240_000L
        )
        assertFalse(item.isTrialAudio)
    }

    @Test
    fun voicePromptAndShortClipsAreIdentifiedAsTrial() {
        val promptClip = SongUrlItem(
            id = 28823239,
            url = "https://music.126.net/prompt.mp3",
            time = 15_000L
        )
        assertTrue(promptClip.isTrialAudio)

        val sixtySecondClip = SongUrlItem(
            id = 28823239,
            url = "https://music.126.net/sixty.mp3",
            time = 60_000L
        )
        assertTrue(sixtySecondClip.isTrialAudio)

        val normalLengthClip = SongUrlItem(
            id = 28823239,
            url = "https://music.126.net/normal.mp3",
            time = 60_001L
        )
        assertFalse(normalLengthClip.isTrialAudio)
    }

    @Test
    fun musicRepTsUrlIsIdentifiedAsTrial() {
        val item = SongUrlItem(
            id = 12345,
            url = "https://m701.music.126.net/musicrep-ts/target.mp3",
            time = 180_000L
        )
        assertTrue(item.isTrialAudio)
    }

    @Test
    fun freeTrialInfoAndPrivilegeAreIdentifiedAsTrial() {
        val itemWithInfo = SongUrlItem(
            id = 12345,
            url = "https://music.126.net/song.mp3",
            time = 180_000L,
            freeTrialInfo = FreeTrialInfo(start = 0, end = 30)
        )
        assertTrue(itemWithInfo.isTrialAudio)

        val itemWithPrivilege = SongUrlItem(
            id = 12345,
            url = "https://music.126.net/song.mp3",
            time = 180_000L,
            freeTrialPrivilege = FreeTrialPrivilege(cannotListenReason = 1)
        )
        assertTrue(itemWithPrivilege.isTrialAudio)
    }

    @Test
    fun blankUrlIsIdentifiedAsTrial() {
        assertTrue(SongUrlItem(id = 12345, url = null).isTrialAudio)
        assertTrue(SongUrlItem(id = 12345, url = "").isTrialAudio)
        assertTrue(SongUrlItem(id = 12345, url = "   ").isTrialAudio)
    }

    @Test
    fun downloadItemTrialCheckAligns() {
        val validItem = SongDownloadUrlItem(
            id = 12345,
            url = "https://music.126.net/download.flac",
            time = 240_000L
        )
        assertFalse(validItem.isTrialAudio)

        val shortItem = SongDownloadUrlItem(
            id = 12345,
            url = "https://music.126.net/download.flac",
            time = 15_000L
        )
        assertTrue(shortItem.isTrialAudio)

        val tsItem = SongDownloadUrlItem(
            id = 12345,
            url = "https://music.126.net/musicrep-ts/download.flac",
            time = 240_000L
        )
        assertTrue(tsItem.isTrialAudio)
    }
}
