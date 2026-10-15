package com.lin0721.linmusic.core.source

// 音源解析结果
data class SourceResult(
    val url: String,
    val platform: MusicPlatform,
    val quality: String = "",
    val pluginId: String? = null,
    val isTrial: Boolean = false,
    // 实际交给插件解析的平台标识，不能用 LX 包装器冒充真实音源平台。
    val resolvedPlatform: MusicPlatform? = null,
    val resolvedSongId: String? = null,
    // 这里只证明候选的元数据；裸 URL 与同平台歌曲 ID 都不能证明返回音频正确。
    val candidate: ExternalTrack? = null
)

// 换源得到的直链是否为试听/口播提示音，与官方接口 isTrialAudio 的 URL 判定保持一致
fun isTrialPlayUrl(url: String): Boolean = url.contains("musicrep-ts", ignoreCase = true)
