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


    private suspend fun preferences(strict: Boolean): SourcePreferences = SourcePreferences(InMemoryPreferencesStore()).also {
        it.saveFallbackEnabled(true); it.saveCommunityPriority(true); it.saveStrictFallbackEnabled(strict)
    }
    private fun router(prefs: SourcePreferences, provider: Provider, official: SongPlaybackInfo? = preview) = AudioSourceRouter(
        Delegate(official, target), UnmApiClient(), prefs, SettingsPreferences(InMemoryPreferencesStore()),
        NetworkStateProvider { true }, listOf(provider))

    @Test fun ordinaryModePreservesBareUrlCompatibility() = runBlocking {
        val info = router(preferences(false), Provider(bareResult)).getSongPlaybackInfo(100).first().getOrThrow()
        assertEquals(PlaybackSourceKind.COMMUNITY, info.sourceKind); assertFalse(info.strictVerified)
    }
    @Test fun fullOfficialAudioDoesNotNeedAlternateSources() = runBlocking {
        val provider = Provider(bareResult); val full = preview.copy(isFreeTrial = false)
        assertEquals(full, router(preferences(true), provider, full).getSongPlaybackInfo(100).first().getOrThrow())
        assertEquals(0, provider.calls)
    }
    @Test fun matchingMetadataWorksWithoutAnyOfficialAudioOrPreview() = runBlocking {
        val provider = Provider(candidateResult)
        val info = router(preferences(true), provider, null).getSongPlaybackInfo(100).first().getOrThrow()
        assertEquals(candidateResult.url, info.url); assertTrue(info.strictVerified)
        assertEquals(PlaybackSourceKind.STRICT_VERIFIED, info.sourceKind)
        assertEquals(64, info.cacheIdentity?.length); assertEquals(1, provider.calls)
    }
    @Test fun missingPreviewOffsetDoesNotBlockMetadataMatching() = runBlocking {
        val info = router(preferences(true), Provider(candidateResult), preview.copy(referenceStartMs = null))
            .getSongPlaybackInfo(100).first().getOrThrow()
        assertTrue(info.strictVerified); assertFalse(info.isFreeTrial)
    }
    @Test fun bareUrlIsNeverACompleteMetadataMatch() = runBlocking {
        val info = router(preferences(true), Provider(bareResult)).getSongPlaybackInfo(100).first().getOrThrow()
        assertEquals(preview.url, info.url); assertFalse(info.strictVerified)
        assertTrue(router(preferences(true), Provider(bareResult), null).getSongPlaybackInfo(100).first().isFailure)
    }
    @Test fun wrongSongArtistAlbumDurationOrLiveVersionCannotPass() = runBlocking {
        listOf(candidate.copy(name = "Other"), candidate.copy(artists = "Other", artistList = listOf("Other")),
            candidate.copy(albumName = "Other Album"), candidate.copy(durationMs = 100_000),
            candidate.copy(name = "龙拳 (Live)"), candidate.copy(albumName = "八度空间 (Live)"))
            .forEach { wrong ->
                assertTrue(router(preferences(true), Provider(candidateResult.copy(candidate = wrong)), null)
                    .getSongPlaybackInfo(100).first().isFailure)
            }
    }
    @Test fun remasteredAndDeluxeEditionsOfTheSameAlbumPass() = runBlocking {
        val remaster = candidate.copy(name = "龙拳 (2014 Remaster)", albumName = "八度空间 (Deluxe Edition)")
        assertTrue(router(preferences(true), Provider(candidateResult.copy(candidate = remaster)), null)
            .getSongPlaybackInfo(100).first().getOrThrow().strictVerified)
    }
    @Test fun candidatePlatformAndSongIdMustMatchTheResolvedSource() = runBlocking {
        listOf(candidateResult.copy(resolvedSongId = "OTHER"), candidateResult.copy(resolvedPlatform = MusicPlatform.KUWO))
            .forEach { wrong -> assertTrue(router(preferences(true), Provider(wrong), null)
                .getSongPlaybackInfo(100).first().isFailure) }
    }
    @Test fun proofIsBoundToTheResolvedUrl() = runBlocking {
        val prefs = preferences(true)
        val a = router(prefs, Provider(candidateResult), null).getSongPlaybackInfo(100).first().getOrThrow()
        val b = router(prefs, Provider(candidateResult.copy(url = "https://community.invalid/other.mp3")), null)
            .getSongPlaybackInfo(100).first().getOrThrow()
        assertFalse(a.cacheIdentity == b.cacheIdentity)
    }
    @Test fun changingModeDuringResolutionDiscardsTheOldResult() = runBlocking {
        val prefs = preferences(true)
        val provider = Provider(candidateResult) { prefs.saveStrictFallbackEnabled(false) }
        assertTrue(router(prefs, provider, null).getSongPlaybackInfo(100).first().isFailure)
    }
    @Test fun cancellationPropagatesRatherThanTryingOtherSources() = runBlocking {
        val provider = Provider(bareResult) { throw CancellationException("cancelled") }
        var cancelled = false
        try { router(preferences(true), provider, null).getSongPlaybackInfo(100).first() }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }
}
