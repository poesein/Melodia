package com.lin0721.linmusic.feature.localmusic.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.MediaMetadataRetriever
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.localmusic.LocalSongMetadata
import com.lin0721.linmusic.feature.localmusic.domain.LocalAudioCandidate
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.feature.localmusic.domain.PreferredLocalAudioMatcher
import com.lin0721.linmusic.feature.localmusic.domain.resolvePreferredLocalAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.preferredLocalAudioDataStore by preferencesDataStore(name = "preferred_local_audio")

/** Only stores references. These files never become Melodia download records. */
class PreferredLocalAudioRepository(
    private val context: Context,
    private val library: LocalLibraryRepository
) {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val automaticKey = booleanPreferencesKey("automatic")
    private val excludedKey = stringSetPreferencesKey("automatic_excluded_song_ids")
    private fun linkKey(songId: Long) = stringPreferencesKey("song_uri_$songId")
    private fun durationKey(songId: Long) = longPreferencesKey("song_duration_$songId")
    private fun titleKey(songId: Long) = stringPreferencesKey("song_title_$songId")
    private fun artistKey(songId: Long) = stringPreferencesKey("song_artist_$songId")

    val enabled: Flow<Boolean> = context.preferredLocalAudioDataStore.data.map { it[enabledKey] ?: true }
    val automaticEnabled: Flow<Boolean> = context.preferredLocalAudioDataStore.data.map { it[automaticKey] ?: true }
    val changes: Flow<Unit> = combine(context.preferredLocalAudioDataStore.data, library.tracks) { _, _ -> Unit }
    fun manualUri(songId: Long): Flow<String?> = context.preferredLocalAudioDataStore.data.map { it[linkKey(songId)] }

    suspend fun setEnabled(value: Boolean) { context.preferredLocalAudioDataStore.edit { it[enabledKey] = value } }
    suspend fun setAutomaticEnabled(value: Boolean) { context.preferredLocalAudioDataStore.edit { it[automaticKey] = value } }

    suspend fun link(songId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (songId <= 0L || uri.scheme != "content") return@withContext false
        try {
            // OpenDocument grants read access; retain it across restarts, without requesting write access.
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!isReadable(uri.toString())) return@withContext false
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(context, uri)
                if ((metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) <= 0L) {
                    return@withContext false
                }
            } finally { metadata.release() }
            context.preferredLocalAudioDataStore.edit {
                it[linkKey(songId)] = uri.toString()
                it[excludedKey] = it[excludedKey].orEmpty() - songId.toString()
            }
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    suspend fun unlink(songId: Long) {
        context.preferredLocalAudioDataStore.edit {
            it.remove(linkKey(songId))
            // Explicitly unlinking must not immediately reselect the same file by metadata.
            it[excludedKey] = it[excludedKey].orEmpty() + songId.toString()
        }
    }

    suspend fun allowAutomaticMatch(songId: Long) {
        context.preferredLocalAudioDataStore.edit { it[excludedKey] = it[excludedKey].orEmpty() - songId.toString() }
    }

    fun automaticExcluded(songId: Long): Flow<Boolean> = context.preferredLocalAudioDataStore.data
        .map { songId.toString() in it[excludedKey].orEmpty() }

    suspend fun preferredUri(
        songId: Long,
        title: String,
        artist: String,
        loadMetadata: suspend () -> LocalSongMetadata? = { null }
    ): String? = withContext(Dispatchers.IO) {
        if (songId <= 0L) return@withContext null
        val prefs = context.preferredLocalAudioDataStore.data.first()
        resolvePreferredLocalAudio(
            enabled = prefs[enabledKey] ?: true,
            manualUri = prefs[linkKey(songId)],
            automaticEnabled = (prefs[automaticKey] ?: true) && songId.toString() !in prefs[excludedKey].orEmpty(),
            automaticUri = {
                val candidates = library.tracks.first().filter { it.source != LocalTrackSource.MELODIA_DOWNLOAD }
                    .map { LocalAudioCandidate(it.uri.toString(), it.title, it.artist, it.durationMs) }
                    .filter { PreferredLocalAudioMatcher.sameTitle(title, it.title) }
                if (candidates.isEmpty()) null else {
                    val cachedDuration = prefs[durationKey(songId)]
                    val metadata = if (cachedDuration != null) LocalSongMetadata(
                        prefs[titleKey(songId)] ?: title, prefs[artistKey(songId)] ?: artist, cachedDuration
                    ) else loadMetadata()?.takeIf { it.durationMs > 0L }?.also { value ->
                        context.preferredLocalAudioDataStore.edit {
                            it[durationKey(songId)] = value.durationMs
                            it[titleKey(songId)] = value.title
                            it[artistKey(songId)] = value.artist
                        }
                    }
                    metadata?.let { PreferredLocalAudioMatcher.uniqueMatch(it.title, it.artist, it.durationMs, candidates) }
                }
            },
            isReadable = ::isReadable
        )
    }

    // Used by song-list offline gates before a queue exists. Automatic matches require previously verified online metadata.
    suspend fun knownPlayableIds(ids: Collection<Long>): Set<Long> = withContext(Dispatchers.IO) {
        val prefs = context.preferredLocalAudioDataStore.data.first()
        if (prefs[enabledKey] == false) return@withContext emptySet()
        val candidates = if (prefs[automaticKey] != false) library.tracks.first()
            .filter { it.source != LocalTrackSource.MELODIA_DOWNLOAD }
            .map { LocalAudioCandidate(it.uri.toString(), it.title, it.artist, it.durationMs) }
            else emptyList()
        ids.filterTo(HashSet()) { songId ->
            resolvePreferredLocalAudio(
                enabled = songId > 0L,
                manualUri = prefs[linkKey(songId)],
                automaticEnabled = prefs[automaticKey] != false && songId.toString() !in prefs[excludedKey].orEmpty(),
                automaticUri = {
                    PreferredLocalAudioMatcher.uniqueMatch(
                        prefs[titleKey(songId)].orEmpty(), prefs[artistKey(songId)].orEmpty(),
                        prefs[durationKey(songId)] ?: 0L, candidates
                    )
                },
                isReadable = ::isReadable
            ) != null
        }
    }

    private suspend fun isReadable(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { it.length != 0L } ?: false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
}
