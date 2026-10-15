package com.lin0721.linmusic.feature.localmusic.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.localAlbumAssociationDataStore by preferencesDataStore(name = "local_album_associations")

/** A user-confirmed album scope and its complete online track cache; separate from song references. */
@Serializable
data class LocalAlbumAssociation(
    val localAlbumKey: String,
    val album: Album,
    val tracks: List<Track>,
)

class LocalAlbumAssociationStore(private val context: Context) {
    val associations: Flow<Map<String, LocalAlbumAssociation>> = context.localAlbumAssociationDataStore.data
        .map { prefs ->
            LocalAlbumAssociationCodec.decodeEntries(prefs.asMap().mapNotNull { (key, value) ->
                (value as? String)?.let { key.name to it }
            }.toMap())
        }.distinctUntilChanged()

    suspend fun put(binding: LocalAlbumAssociation) {
        val encoded = LocalAlbumAssociationCodec.encode(binding)
        context.localAlbumAssociationDataStore.edit { prefs ->
            prefs[stringPreferencesKey(LocalAlbumAssociationCodec.keyFor(binding.localAlbumKey))] = encoded
        }
    }

    suspend fun remove(localAlbumKey: String) {
        context.localAlbumAssociationDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(LocalAlbumAssociationCodec.keyFor(localAlbumKey)))
        }
    }
}

/** Pure codec: one malformed or mismatched entry cannot hide otherwise valid album bindings. */
internal object LocalAlbumAssociationCodec {
    private const val PREFIX = "album_"
    private val json = Json { ignoreUnknownKeys = true }

    // Encode UTF-16 code units so every Kotlin String, including unusual folder characters, stays distinct.
    fun keyFor(localAlbumKey: String): String = buildString(PREFIX.length + localAlbumKey.length * 4) {
        append(PREFIX)
        localAlbumKey.forEach { append(it.code.toString(16).padStart(4, '0')) }
    }

    fun encode(binding: LocalAlbumAssociation): String {
        require(isValid(binding)) { "A valid album and a complete, unique track cache are required" }
        return json.encodeToString(binding)
    }

    fun decodeEntries(entries: Map<String, String>): Map<String, LocalAlbumAssociation> = buildMap {
        entries.forEach { (key, encoded) ->
            if (!key.startsWith(PREFIX)) return@forEach
            val binding = runCatching { json.decodeFromString<LocalAlbumAssociation>(encoded) }.getOrNull()
                ?: return@forEach
            if (isValid(binding) && keyFor(binding.localAlbumKey) == key) put(binding.localAlbumKey, binding)
        }
    }

    private fun isValid(binding: LocalAlbumAssociation): Boolean = binding.localAlbumKey.isNotBlank() &&
        binding.album.id > 0L && binding.tracks.isNotEmpty() &&
        binding.tracks.all { it.id > 0L } && binding.tracks.map { it.id }.distinct().size == binding.tracks.size
}
