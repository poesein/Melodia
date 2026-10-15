package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.data.PlaybackSourceKind
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictPlaybackPolicyTest {
    private fun info(kind: PlaybackSourceKind, verified: Boolean = false, identity: String? = null) =
        SongPlaybackInfo("https://audio.example/recording.flac", sourceKind = kind, strictVerified = verified, cacheIdentity = identity)

    @Test fun `关闭严格模式保持旧未知来源结果可用`() {
        assertTrue(StrictPlaybackPolicy.allowsInfo(false, info(PlaybackSourceKind.UNKNOWN)))
    }

    @Test fun `严格模式保留官方完整音频及官方试听`() {
        assertTrue(StrictPlaybackPolicy.allowsInfo(true, info(PlaybackSourceKind.OFFICIAL)))
        assertTrue(StrictPlaybackPolicy.allowsInfo(true, info(PlaybackSourceKind.OFFICIAL).copy(isFreeTrial = true)))
    }

    @Test fun `严格模式接受具有核验状态和录音身份的结果`() {
        assertTrue(StrictPlaybackPolicy.allowsInfo(true, info(PlaybackSourceKind.STRICT_VERIFIED, true, "recording-proof")))
    }

    @Test fun `仅有已核验类型但缺少核验状态不能放行`() {
        assertFalse(StrictPlaybackPolicy.allowsInfo(true, info(PlaybackSourceKind.STRICT_VERIFIED, false, "recording-proof")))
    }

    @Test fun `核验结果缺少录音身份或仅空白时不能放行`() {
        listOf(null, "", " ").forEach { identity ->
            assertFalse(StrictPlaybackPolicy.allowsInfo(true, info(PlaybackSourceKind.STRICT_VERIFIED, true, identity)))
        }
    }

    @Test fun `社区和远程UNM结果不能仅靠布尔标记冒充核验类型`() {
        listOf(PlaybackSourceKind.COMMUNITY, PlaybackSourceKind.REMOTE_UNM, PlaybackSourceKind.UNKNOWN).forEach { kind ->
            assertFalse(StrictPlaybackPolicy.allowsInfo(true, info(kind, true, "untrusted-metadata")))
        }
    }

    @Test fun `空官方URL不能成为已加载音源证明`() {
        assertFalse(StrictPlaybackPolicy.allowsInfo(true, info(PlaybackSourceKind.OFFICIAL).copy(url = "")))
    }

    @Test fun `HTTP聚合搜索URL不能因为放在localUri字段就成为本地文件`() {
        assertFalse(StrictPlaybackPolicy.isLocalFile("https://audio.example/song.flac"))
        assertFalse(StrictPlaybackPolicy.isLocalFile("http://audio.example/song.mp3"))
    }

    @Test fun `content和file本地文件继续可用且scheme不区分大小写`() {
        assertTrue(StrictPlaybackPolicy.isLocalFile("content://media/external/audio/media/7"))
        assertTrue(StrictPlaybackPolicy.isLocalFile("file:///storage/emulated/0/Music/song.flac"))
        assertTrue(StrictPlaybackPolicy.isLocalFile("CONTENT://provider/document/7"))
    }

    @Test fun `未知或无scheme的直接地址不视为已确认本地文件`() {
        listOf(null, "", "/storage/emulated/0/Music/song.flac", "data:audio/flac;base64,anything").forEach {
            assertFalse(StrictPlaybackPolicy.isLocalFile(it))
        }
    }

    @Test fun `同代相同严格设置才能复用结果`() {
        assertTrue(StrictPlaybackPolicy.isCurrent(PlaybackPolicyToken(7, true), 7, true))
    }

    @Test fun `切换模式后旧网络和预取结果失效即使任务取消没有中止回调`() {
        assertFalse(StrictPlaybackPolicy.isCurrent(PlaybackPolicyToken(7, false), 8, true))
    }

    @Test fun `开关来回切换后旧结果不能因布尔值相同再次生效`() {
        assertFalse(StrictPlaybackPolicy.isCurrent(PlaybackPolicyToken(7, false), 9, false))
    }

    @Test fun `设置已改变而收集器尚未递增代际时也拒绝旧结果`() {
        assertFalse(StrictPlaybackPolicy.isCurrent(PlaybackPolicyToken(7, false), 7, true))
    }

    @Test fun `严格模式禁用历史Media3缓存而关闭时保留缓存行为`() {
        assertFalse(StrictPlaybackPolicy.allowsStreamingCache(true))
        assertTrue(StrictPlaybackPolicy.allowsStreamingCache(false))
    }

    private val currentToken = PlaybackPolicyToken(7, true)
    private val verifiedInfo = info(PlaybackSourceKind.STRICT_VERIFIED, true, "recording-proof")

    @Test fun `恢复播放证明必须绑定队列和已加载曲目及实际URL`() {
        assertTrue(StrictPlaybackPolicy.canResumeNetwork(1, 1, 1, verifiedInfo.url, verifiedInfo, currentToken, currentToken))
    }

    @Test fun `切至B取链期间不能借A的已核验证明恢复旧A`() {
        assertFalse(StrictPlaybackPolicy.canResumeNetwork(1, 1, 2, verifiedInfo.url, verifiedInfo, currentToken, currentToken))
    }

    @Test fun `相同歌曲ID但底层已换成其他URL不能复用证明`() {
        assertFalse(StrictPlaybackPolicy.canResumeNetwork(1, 1, 1, "https://audio.example/different.flac", verifiedInfo, currentToken, currentToken))
    }

    @Test fun `证明所属ID不同或没有当前队列时拒绝恢复`() {
        assertFalse(StrictPlaybackPolicy.canResumeNetwork(2, 1, 1, verifiedInfo.url, verifiedInfo, currentToken, currentToken))
        assertFalse(StrictPlaybackPolicy.canResumeNetwork(1, 1, null, verifiedInfo.url, verifiedInfo, currentToken, currentToken))
    }

    @Test fun `取消待切歌后恢复原队列A仍可复用A的有效证明`() {
        assertFalse(StrictPlaybackPolicy.matchesCurrentItem(1, 2))
        assertTrue(StrictPlaybackPolicy.canResumeNetwork(1, 1, 1, verifiedInfo.url, verifiedInfo, currentToken, currentToken))
    }

    @Test fun `外部controller严格模式不能注入媒体项而关闭时保持旧授权`() {
        assertFalse(StrictPlaybackPolicy.canSetMediaItems("external.controller", "our.app", true))
        assertTrue(StrictPlaybackPolicy.canSetMediaItems("external.controller", "our.app", false))
        assertTrue(StrictPlaybackPolicy.canSetMediaItems("our.app", "our.app", true))
    }

    @Test fun `本包Manager控制器放行底层播放且外部与通知都走Manager`() {
        assertFalse(StrictPlaybackPolicy.routeTransportThroughManager("our.app", "our.app", false))
        assertTrue(StrictPlaybackPolicy.routeTransportThroughManager("external.controller", "our.app", false))
        assertTrue(StrictPlaybackPolicy.routeTransportThroughManager("our.app", "our.app", true))
    }

    @Test fun `恢复播放证明也必须满足当前代际和核验来源要求`() {
        assertFalse(StrictPlaybackPolicy.canResumeNetwork(1, 1, 1, verifiedInfo.url, verifiedInfo, PlaybackPolicyToken(6, true), currentToken))
        assertFalse(StrictPlaybackPolicy.canResumeNetwork(1, 1, 1, verifiedInfo.url, info(PlaybackSourceKind.COMMUNITY), currentToken, currentToken))
    }
}
