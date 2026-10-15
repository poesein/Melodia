package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo

// 只能由宿主对实际候选音频与可信官方片段做 PCM 比对后生成；插件的自报元数据不能生成此证据。
data class StrictAudioVerification(
    val cacheIdentity: String,
    val comparedDurationMs: Long,
    val normalizedCorrelation: Double
) {
    companion object {
        const val MIN_NORMALIZED_CORRELATION = 0.94
        const val MIN_COMPARED_DURATION_MS = 6_000L
    }
}

interface StrictAudioVerifier {
    // 不能确认时返回 null；不能将无官方片段、仅标签一致或裸 URL 当成核验通过。取消须向上传播。
    suspend fun verify(
        targetTrack: Track,
        candidateUrl: String,
        officialPreview: SongPlaybackInfo
    ): StrictAudioVerification?
}
