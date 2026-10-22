package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.player.data.PlaybackSourceKind
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest

private const val TAG = "AudioSourceRouter"

// PlaybackRepository 装饰器，在官方接口失败或仅为试听片段时自动通过 UNM 服务换源
class AudioSourceRouter(
    private val delegate: PlaybackRepository,
    private val unmApiClient: UnmApiClient,
    private val sourcePreferences: SourcePreferences,
    private val settingsPreferences: SettingsPreferences,
    private val networkStateProvider: NetworkStateProvider,
    private val providers: List<AudioSourceProvider> = emptyList()
) : PlaybackRepository {

    override fun getSongPlaybackInfo(songId: Long): Flow<Result<SongPlaybackInfo>> = flow {
        val fallbackEnabled = sourcePreferences.fallbackEnabled.first()
        val strict = sourcePreferences.strictFallbackEnabled.first()

        suspend fun emitCurrent(result: Result<SongPlaybackInfo>) {
            // 设置切换后，旧协程的成功结果不能进入新的策略；播放器同时按 epoch 取消预取和活动请求。
            if (sourcePreferences.strictFallbackEnabled.first() != strict) {
                emit(Result.failure(AppError.BizError(-1, "音源严格模式已变化，请重新播放")))
            } else emit(result)
        }

        // 1. 先尝试官方接口获取直链
        val officialInfoResult = delegate.getSongPlaybackInfo(songId).first()
        val officialInfo = officialInfoResult.getOrNull()

        // 官方返回完整音频且非试听时，直接使用官方链接
        val officialTrusted = officialInfo?.sourceKind == PlaybackSourceKind.OFFICIAL
        if (officialInfo != null && !officialInfo.isFreeTrial && officialInfo.url.isNotBlank() && (!strict || officialTrusted)) {
            emitCurrent(Result.success(officialInfo))
            return@flow
        }

        // 官方失败或仅为试听，检查是否启用换源
        if (!fallbackEnabled) {
            if (officialInfo != null && officialInfo.url.isNotBlank() && (!strict || officialTrusted)) {
                AppLogger.d(TAG, "换源未启用，返回官方试听链接 songId=$songId")
                emitCurrent(Result.success(officialInfo))
            } else {
                AppLogger.d(TAG, "换源未启用，返回官方失败结果 songId=$songId")
                emitCurrent(Result.failure(officialInfoResult.exceptionOrNull() ?: AppError.BizError(-1, "无法获取播放链接")))
            }
            return@flow
        }

        // 2. 需换源且总开关开启
        AppLogger.i(TAG, "开始换源流程: songId=$songId (官方为试听: ${officialInfo?.isFreeTrial})")

        val communityPriority = sourcePreferences.communityPriority.first()

        // 辅助函数：解析社区扩展源插件
        suspend fun tryResolveCommunity(): SongPlaybackInfo? {
            if (providers.isEmpty()) return null
            val track = delegate.getSongDetail(songId).first().getOrNull() ?: return null
            val quality = if (networkStateProvider.isWifiConnected()) {
                settingsPreferences.wifiQuality.first()
            } else {
                settingsPreferences.mobileQuality.first()
            }
            for (p in providers) {
                try {
                    val r = p.resolveUrl(
                        songId = songId.toString(),
                        songName = track.name,
                        artists = track.ar.joinToString("/") { it.name },
                        albumName = track.al.name,
                        durationMs = track.dt,
                        quality = quality
                    )
                    if (r != null && r.url.isNotBlank() && !r.isTrial && !isTrialPlayUrl(r.url)) {
                        if (!strict) return SongPlaybackInfo(url = r.url, sourceKind = PlaybackSourceKind.COMMUNITY)
                        val candidate = r.candidate ?: continue
                        val resolved = r.resolvedPlatform ?: continue
                        val resolvedId = r.resolvedSongId?.takeIf { it.isNotBlank() } ?: continue
                        if (candidate.platform != resolved || (candidate.hash?.takeIf { it.isNotBlank() } ?: candidate.id) != resolvedId) continue
                        if (!StrictSourceCandidatePolicy.matches(candidate, track.name, track.ar.map { it.name }, track.al.name, track.dt)) continue
                        val identity = MessageDigest.getInstance("SHA-256").digest(
                            "strict-metadata-v2|$songId|${resolved.key}|$resolvedId|${r.url}".toByteArray()
                        ).joinToString("") { "%02x".format(it) }
                        return SongPlaybackInfo(url = r.url, sourceKind = PlaybackSourceKind.STRICT_VERIFIED,
                            strictVerified = true, cacheIdentity = identity)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "${p.platform.displayName} 解析失败: ${e.javaClass.simpleName}")
                }
            }
            return null
        }

        // 辅助函数：解析远程 UNM 兜底服务
        suspend fun tryResolveRemoteUnm(): SongPlaybackInfo? {
            // Bare UNM URLs do not carry the required song/artist/album candidate metadata.
            if (strict) return null
            val remoteFallbackEnabled = sourcePreferences.unmRemoteFallbackEnabled.first()
            val serverUrl = sourcePreferences.unmServerUrl.first().trim()
            if (!remoteFallbackEnabled || serverUrl.isBlank()) return null
            AppLogger.d(TAG, "尝试远程 UNM 服务 songId=$songId")
            val remoteResult = unmApiClient.matchSong(serverUrl, songId, null)
            if (remoteResult != null && remoteResult.url.isNotBlank() && !isTrialPlayUrl(remoteResult.url)) {
                AppLogger.i(TAG, "远程 UNM 服务解析成功: songId=$songId 来源=${remoteResult.source}")
                return SongPlaybackInfo(url = remoteResult.url, sourceKind = PlaybackSourceKind.REMOTE_UNM)
            }
            return null
        }

        val matched = if (communityPriority) {
            // 社区源优先：先尝试社区扩展插件，未命中再走远程 UNM
            tryResolveCommunity() ?: tryResolveRemoteUnm()
        } else {
            // 远程 UNM 优先：先尝试远程 UNM，未命中再走社区扩展插件
            tryResolveRemoteUnm() ?: tryResolveCommunity()
        }

        // 3. 结果判定
        if (matched != null) {
            AppLogger.i(TAG, "换源成功: songId=$songId 最终来源=${matched.sourceKind}")
            emitCurrent(Result.success(matched))
        } else {
            AppLogger.w(TAG, "所有换源均未命中: songId=$songId")
            if (officialInfo != null && officialInfo.url.isNotBlank() && (!strict || officialTrusted)) {
                AppLogger.i(TAG, "兜底返回官方试听链接: songId=$songId")
                emitCurrent(Result.success(officialInfo.copy(trialFallback = officialInfo.isFreeTrial)))
            } else {
                val message = if (strict) "严格模式未找到歌曲、歌手和专辑一致的可用音源" else "换源未能获取到可用播放链接"
                emitCurrent(Result.failure(officialInfoResult.exceptionOrNull() ?: AppError.BizError(-1, message)))
            }
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        AppLogger.e(TAG, "换源流程异常 songId=$songId", e)
        emit(Result.failure(e))
    }

    override fun getSongUrl(songId: Long): Flow<Result<String>> =
        getSongPlaybackInfo(songId).map { result -> result.map { it.url } }

    // 委托其余方法
    override fun getLyrics(songId: Long) = delegate.getLyrics(songId)
    override fun getRawLyrics(songId: Long) = delegate.getRawLyrics(songId)
    override fun getSongDetail(songId: Long) = delegate.getSongDetail(songId)
    override fun getSimilarSongs(songId: Long) = delegate.getSimilarSongs(songId)
    override fun getIntelligenceSongs(songId: Long, playlistId: Long) = delegate.getIntelligenceSongs(songId, playlistId)
    override fun reportStartPlay(songId: Long, source: PlaySource?) = delegate.reportStartPlay(songId, source)
    override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?) = delegate.reportPlayEnd(songId, playedSeconds, source)
    override val playlistRecorded: SharedFlow<Long> get() = delegate.playlistRecorded
}
