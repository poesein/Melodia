package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.data.PlaybackSourceKind
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import java.util.Locale

/** Generation and settings snapshot travel with every asynchronous playback result. */
internal data class PlaybackPolicyToken(val epoch: Long, val strict: Boolean)

internal object StrictPlaybackPolicy {
    // QueueItem.localUri also stores aggregate-search HTTP links, so non-null is not proof of locality.
    fun isLocalFile(uri: String?): Boolean = uri?.substringBefore(':', "")?.lowercase(Locale.ROOT) in setOf("content", "file")

    fun allowsInfo(strict: Boolean, info: SongPlaybackInfo): Boolean = !strict ||
        (info.url.isNotBlank() && (info.sourceKind == PlaybackSourceKind.OFFICIAL ||
            (info.sourceKind == PlaybackSourceKind.STRICT_VERIFIED && info.strictVerified && !info.cacheIdentity.isNullOrBlank())))

    fun isCurrent(token: PlaybackPolicyToken, epoch: Long, strict: Boolean): Boolean =
        token.epoch == epoch && token.strict == strict

    fun matchesCurrentItem(loadedSongId: Long?, queuedSongId: Long?): Boolean =
        loadedSongId != null && loadedSongId == queuedSongId

    fun canResumeNetwork(
        proofSongId: Long,
        loadedSongId: Long?,
        queuedSongId: Long?,
        loadedUri: String?,
        info: SongPlaybackInfo,
        proofToken: PlaybackPolicyToken,
        currentToken: PlaybackPolicyToken
    ): Boolean = matchesCurrentItem(loadedSongId, queuedSongId) && proofSongId == loadedSongId &&
        loadedUri == info.url && isCurrent(proofToken, currentToken.epoch, currentToken.strict) &&
        allowsInfo(currentToken.strict, info)

    fun canSetMediaItems(controllerPackage: String, appPackage: String, strict: Boolean): Boolean =
        !strict || controllerPackage == appPackage

    // Notification controls may share the app package; their transport requests still go through Manager.
    fun routeTransportThroughManager(controllerPackage: String, appPackage: String, isNotification: Boolean): Boolean =
        isNotification || controllerPackage != appPackage

    // Historical Media3 cache entries carry no verification provenance; retain them on disk for non-strict mode.
    fun allowsStreamingCache(strict: Boolean): Boolean = !strict
}
