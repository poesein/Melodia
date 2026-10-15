package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.PlaybackSourceKind
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSourceRouterStrictTest {
    private val target = Track(id = 100, name = "龙拳", ar = listOf(Artist(name = "周杰伦")),
        al = Album(name = "八度空间"), dt = 274_000)
    private val preview = SongPlaybackInfo("https://official.invalid/preview.mp3", isFreeTrial = true,
        sourceKind = PlaybackSourceKind.OFFICIAL, referenceStartMs = 30_000)
    private val bareResult = SourceResult("https://community.invalid/song.mp3", MusicPlatform.LX)
    private val candidate = ExternalTrack("QQ1", "龙拳", "周杰伦", artistList = listOf("周杰伦"),
        albumName = "八度空间", durationMs = 274_000, platform = MusicPlatform.QQ)
    private val candidateResult = bareResult.copy(resolvedPlatform = MusicPlatform.QQ,
        resolvedSongId = "QQ1", candidate = candidate)

    private class Delegate(private val info: SongPlaybackInfo?, private val track: Track) : PlaybackRepository {
        override fun getSongPlaybackInfo(songId: Long) = flowOf(info?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("official unavailable")))
        override fun getSongUrl(songId: Long) = flowOf(info?.let { Result.success(it.url) }
            ?: Result.failure(IllegalStateException("official unavailable")))
        override fun getSongDetail(songId: Long) = flowOf(Result.success(track))
        override fun getLyrics(songId: Long): Flow<Result<List<LyricLine>>> = flowOf(Result.success(emptyList()))
        override fun getRawLyrics(songId: Long) = flowOf(Result.success(""))
        override fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>> = flowOf(Result.success(emptyList()))
        override fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>> = flowOf(Result.success(emptyList()))
        override fun reportStartPlay(songId: Long, source: PlaySource?) = flowOf(Result.success(Unit))
        override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?) = flowOf(Result.success(Unit))
        override val playlistRecorded: SharedFlow<Long> = MutableSharedFlow()
    }

    private class Provider(private val result: SourceResult?, private val onResolve: suspend () -> Unit = {}) : AudioSourceProvider {
        var calls = 0
        override val platform = MusicPlatform.LX
        override suspend fun resolveUrl(songId: String, songName: String, artists: String, albumName: String?,
            durationMs: Long, quality: String, origin: MusicPlatform): SourceResult? {
            calls++
            onResolve()
            return result
        }
        override suspend fun search(keyword: String, offset: Int, limit: Int) = emptyList<ExternalTrack>()
    }

    private class Verifier(private val accepted: Boolean = true, private val onVerify: suspend () -> Unit = {}) : StrictAudioVerifier {
        var calls = 0
        var referenceStart: Long? = null
        override suspend fun verify(targetTrack: Track, candidateUrl: String, officialPreview: SongPlaybackInfo): StrictAudioVerification? {
            calls++
            referenceStart = officialPreview.referenceStartMs
            onVerify()
            // 流程单测中的宿主 stub；真正的 PCM 算法由 Android 独立回归覆盖。
            return if (accepted) StrictAudioVerification("bound-evidence-for-100", 6_000, 0.995) else null
        }
    }

    private suspend fun preferences(strict: Boolean): SourcePreferences = SourcePreferences(InMemoryPreferencesStore()).also {
        it.saveFallbackEnabled(true)
        it.saveCommunityPriority(true)
        it.saveStrictFallbackEnabled(strict)
    }

    private fun router(prefs: SourcePreferences, provider: Provider, verifier: StrictAudioVerifier? = null,
        official: SongPlaybackInfo? = preview): AudioSourceRouter = AudioSourceRouter(
        Delegate(official, target), UnmApiClient(), prefs, SettingsPreferences(InMemoryPreferencesStore()),
        NetworkStateProvider { true }, listOf(provider), verifier
    )

    @Test fun `关闭严格模式时保留旧裸 URL 播放行为但不冒充已核验`() = runBlocking {
        val provider = Provider(bareResult)
        val info = router(preferences(false), provider).getSongPlaybackInfo(100).first().getOrThrow()
        assertEquals(bareResult.url, info.url)
        assertEquals(PlaybackSourceKind.COMMUNITY, info.sourceKind)
        assertFalse(info.strictVerified)
    }

    @Test fun `严格完整官方结果可直接播放无需插件或片段核验`() = runBlocking {
        val provider = Provider(bareResult)
        val verifier = Verifier()
        val full = preview.copy(isFreeTrial = false)
        val info = router(preferences(true), provider, verifier, full).getSongPlaybackInfo(100).first().getOrThrow()
        assertEquals(full, info)
        assertEquals(0, provider.calls)
        assertEquals(0, verifier.calls)
    }

    @Test fun `未显式声明来源的旧结果不能当可信官方直链`() = runBlocking {
        val provider = Provider(bareResult)
        val unknown = SongPlaybackInfo("https://unknown.invalid/full.mp3")
        assertTrue(router(preferences(true), provider, Verifier(), unknown).getSongPlaybackInfo(100).first().isFailure)
        assertEquals(0, provider.calls)
    }

    @Test fun `缺宿主核验器时保留安全官方试听且不调用插件`() = runBlocking {
        val provider = Provider(candidateResult)
        val info = router(preferences(true), provider).getSongPlaybackInfo(100).first().getOrThrow()
        assertEquals(preview.url, info.url)
        assertTrue(info.trialFallback)
        assertEquals(0, provider.calls)
    }

    @Test fun `无可信官方参考时严格拒绝社区音频`() = runBlocking {
        val provider = Provider(candidateResult)
        assertTrue(router(preferences(true), provider, Verifier(), null).getSongPlaybackInfo(100).first().isFailure)
        assertEquals(0, provider.calls)
    }

    @Test fun `试听起点缺失或非法时不等待插件解析而直接保留官方试听`() = runBlocking {
        listOf<Long?>(null, -1L).forEach { start ->
            val provider = Provider(candidateResult)
            val verifier = Verifier()
            val official = preview.copy(referenceStartMs = start)
            val info = router(preferences(true), provider, verifier, official).getSongPlaybackInfo(100).first().getOrThrow()
            assertEquals(official.url, info.url)
            assertTrue(info.trialFallback)
            assertEquals(0, provider.calls)
            assertEquals(0, verifier.calls)
        }
    }

    @Test fun `裸 URL 不能靠存在核验器自动升级证据`() = runBlocking {
        val verifier = Verifier()
        val info = router(preferences(true), Provider(bareResult), verifier).getSongPlaybackInfo(100).first().getOrThrow()
        assertEquals(preview.url, info.url)
        assertEquals(0, verifier.calls)
        assertFalse(info.strictVerified)
    }

    @Test fun `候选来源和歌曲标识不一致时在请求音频前拒绝`() = runBlocking {
        val verifier = Verifier()
        val mismatched = candidateResult.copy(resolvedSongId = "OTHER")
        val info = router(preferences(true), Provider(mismatched), verifier).getSongPlaybackInfo(100).first().getOrThrow()
        assertEquals(preview.url, info.url)
        assertEquals(0, verifier.calls)
    }

    @Test fun `候选元数据正确仍必须实际音频比对通过`() = runBlocking {
        val verifier = Verifier(accepted = false)
        val info = router(preferences(true), Provider(candidateResult), verifier).getSongPlaybackInfo(100).first().getOrThrow()
        assertEquals(preview.url, info.url)
        assertEquals(1, verifier.calls)
        assertFalse(info.strictVerified)
    }

    @Test fun `宿主实际核验通过产生可播放的严格来源与绑定证据`() = runBlocking {
        val verifier = Verifier()
        val info = router(preferences(true), Provider(candidateResult), verifier).getSongPlaybackInfo(100).first().getOrThrow()
        assertEquals(candidateResult.url, info.url)
        assertEquals(PlaybackSourceKind.STRICT_VERIFIED, info.sourceKind)
        assertTrue(info.strictVerified)
        assertEquals("bound-evidence-for-100", info.cacheIdentity)
        assertEquals(30_000L, verifier.referenceStart)
    }

    @Test fun `无绑定或过短低分非有限的宿主证据均不能放行`() = runBlocking {
        listOf(StrictAudioVerification("", 6_000, 0.995), StrictAudioVerification("bound", 3_000, 0.995),
            StrictAudioVerification("bound", 6_000, 0.93), StrictAudioVerification("bound", 6_000, Double.NaN),
            StrictAudioVerification("bound", 6_000, Double.POSITIVE_INFINITY)).forEach { evidence ->
            val verifier = object : StrictAudioVerifier {
                override suspend fun verify(targetTrack: Track, candidateUrl: String,
                    officialPreview: SongPlaybackInfo) = evidence
            }
            val info = router(preferences(true), Provider(candidateResult), verifier).getSongPlaybackInfo(100).first().getOrThrow()
            assertEquals(preview.url, info.url)
            assertFalse(info.strictVerified)
        }
    }

    @Test fun `解析过程中开启严格模式不能返回旧裸 URL`() = runBlocking {
        val prefs = preferences(false)
        val provider = Provider(bareResult) { prefs.saveStrictFallbackEnabled(true) }
        assertTrue(router(prefs, provider).getSongPlaybackInfo(100).first().isFailure)
    }

    @Test fun `核验过程中切换模式也丢弃旧结果`() = runBlocking {
        val prefs = preferences(true)
        val verifier = Verifier { prefs.saveStrictFallbackEnabled(false) }
        assertTrue(router(prefs, Provider(candidateResult), verifier).getSongPlaybackInfo(100).first().isFailure)
    }

    @Test fun `取消不能被吞掉后继续尝试其他来源`() = runBlocking {
        val provider = Provider(bareResult) { throw CancellationException("cancelled") }
        var cancelled = false
        try { router(preferences(false), provider).getSongPlaybackInfo(100).first() }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }
}
