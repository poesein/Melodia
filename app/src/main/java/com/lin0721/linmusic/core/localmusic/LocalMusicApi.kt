package com.lin0721.linmusic.core.localmusic

import android.net.Uri

data class LocalSongMetadata(val title: String, val artist: String, val durationMs: Long, val album: String? = null)

// core 只依赖此接口，实现在 feature/localmusic
interface LocalMusicApi {

    suspend fun coverUriFor(sourceUri: Uri): Uri?

    suspend fun readLyrics(sourceUri: String): String?

    suspend fun preferredUri(songId: Long, title: String, artist: String, loadMetadata: suspend () -> LocalSongMetadata? = { null }): String? = null
}
