package com.lin0721.linmusic.feature.artist.data.catalog

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistAlbumCorrection
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistAlbumCorrections
import java.io.IOException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.albumCorrectionsStore by preferencesDataStore(name = "artist_album_corrections")

/** Independent, additive preferences: no catalogue cache, original preference, or Room migration. */
class ArtistAlbumCorrectionsRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.albumCorrectionsStore)

    private val json = Json { ignoreUnknownKeys = true }
    private fun key(artistId: Long) = stringPreferencesKey("artist_${artistId}_corrections")

    fun observe(artistId: Long) = store.data.map { decode(it[key(artistId)]) }.distinctUntilChanged()

    suspend fun setEnabled(artistId: Long, enabled: Boolean) = update(artistId) { it.copy(enabled = enabled) }

    suspend fun setDecision(artistId: Long, album: ArtistAlbum, show: Boolean, enableCorrections: Boolean = false) {
        require(album.id > 0 && album.name.isNotBlank() && album.name.length <= 2000) { "专辑信息无效，请重新加载" }
        update(artistId) { previous ->
            val old = previous.entries.firstOrNull { it.album.id == album.id }?.album
            // Album detail carries less metadata than artist-list rows. Quick-add must not erase
            // the saved date/count or cover when it updates an existing show/hide decision.
            val snapshot = if (old == null) album else album.copy(
                picUrl = album.picUrl.ifBlank { old.picUrl },
                publishTime = album.publishTime.takeIf { it > 0 } ?: old.publishTime,
                size = album.size.takeIf { it > 0 } ?: old.size,
                type = album.type ?: old.type, subType = album.subType ?: old.subType,
                alias = album.alias ?: old.alias, transNames = album.transNames ?: old.transNames)
            val entries = previous.entries.filterNot { it.album.id == album.id } + ArtistAlbumCorrection(snapshot, show)
            require(entries.size <= 5000) { "此歌手的手动记录已达上限" }
            previous.copy(entries = entries, enabled = previous.enabled || enableCorrections)
        }
    }

    /** Per-album automatic mode is reversible too: preserve the last explicit show/hide decision. */
    suspend fun setEntryActive(artistId: Long, albumId: Long, active: Boolean) = update(artistId) { previous ->
        previous.copy(entries = previous.entries.map { if (it.album.id == albumId) it.copy(active = active) else it })
    }

    private suspend fun update(artistId: Long, change: (ArtistAlbumCorrections) -> ArtistAlbumCorrections) {
        require(artistId > 0L) { "歌手信息无效，请重新打开页面" }
        store.edit { values ->
            // Re-read inside the transaction, so rapid edits and concurrent screens cannot erase
            // another choice. Malformed/future data fails closed and is never replaced by defaults.
            val next = json.encodeToString(change(decode(values[key(artistId)])))
            require(next.length <= 2_000_000) { "手动记录过大，未保存本次修改" }
            values[key(artistId)] = next
        }
    }

    private fun decode(raw: String?): ArtistAlbumCorrections {
        if (raw == null) return ArtistAlbumCorrections()
        val result = try { json.decodeFromString<ArtistAlbumCorrections>(raw) }
        catch (error: Exception) { throw IOException("无法读取已保存的手动记录，原记录已保留", error) }
        if (result.version != 1 || result.entries.any { it.album.id <= 0 } ||
            result.entries.map { it.album.id }.distinct().size != result.entries.size) {
            throw IOException("手动记录格式无法识别，原记录已保留")
        }
        return result
    }
}
