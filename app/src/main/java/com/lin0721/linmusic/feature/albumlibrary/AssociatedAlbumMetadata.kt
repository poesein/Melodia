package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.feature.localmusic.data.LocalAlbumAssociation

/** Reuse the saved online track list, never guess tracks from a local folder name. */
internal fun associatedAlbumMetadata(id: Long, bindings: Collection<LocalAlbumAssociation>): PlaylistDetail? {
    val saved = bindings.filter { binding ->
        binding.album.id == id && binding.tracks.isNotEmpty() &&
            binding.tracks.all { it.id > 0 && (it.al.id == 0L || it.al.id == id) }
    }.maxByOrNull { it.tracks.size } ?: return null
    return PlaylistDetail(id = id, name = saved.album.name, coverImgUrl = saved.album.picUrl,
        tracks = saved.tracks, artists = saved.album.artists, publishTime = saved.album.publishTime)
}
