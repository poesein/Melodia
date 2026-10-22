package com.lin0721.linmusic.feature.playlist.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.playlistTrackSortStore by preferencesDataStore(name = "playlist_track_sort")

data class PlaylistSortTarget(val id: Long, val isAlbum: Boolean = false)

/** A public playlist may be viewed by several accounts; album IDs use a separate namespace. */
data class PlaylistSortScope(val accountId: Long?, val target: PlaylistSortTarget) {
    internal val key: String get() = "v1_${accountId?.let { "account_$it" } ?: "guest"}_${if (target.isAlbum) "album" else "playlist"}_${target.id}"
}

internal data class PlaylistSortStorageState(
    val sort: PlaylistTrackSort = PlaylistTrackSort(),
    val loaded: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null
)

/** View-only sorting never writes to the server playlist or the active playback queue. */
class PlaylistTrackSortRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val writerScope: CoroutineScope
) : Closeable {
    constructor(context: Context) : this(context.applicationContext.playlistTrackSortStore,
        CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private data class Accepted(val revision: Long, val value: PlaylistSortStorageState)
    private val accepted = MutableStateFlow<Map<PlaylistSortScope, Accepted>>(emptyMap())
    private val writeMutex = Mutex()
    private val acceptanceLock = Any()
    private var revision = 0L

    private fun key(scope: PlaylistSortScope) = stringPreferencesKey(scope.key)

    fun observe(scope: PlaylistSortScope) = store.data.map { decode(it[key(scope)]) }.distinctUntilChanged()

    /** All retained frames observe the same accepted value, including an in-flight save.
     * Keep accepted entries for this repository's lifetime so a delayed disk emission cannot
     * briefly restore an older order after a transaction completes. This file has one owner.
     */
    internal fun observeState(scope: PlaylistSortScope) = combine(store.data, accepted) { values, changes ->
        changes[scope]?.value ?: try { PlaylistSortStorageState(decode(values[key(scope)])) }
        catch (error: IOException) { PlaylistSortStorageState(loaded = false, error = error.message) }
    }.distinctUntilChanged()

    /** Accepted writes belong to the application repository, not a disposable navigation frame. */
    internal fun submitSave(scope: PlaylistSortScope, sort: PlaylistTrackSort) = enqueue(scope, sort, reset = false)
    internal fun submitReset(scope: PlaylistSortScope) = enqueue(scope, PlaylistTrackSort(), reset = true)

    suspend fun save(scope: PlaylistSortScope, sort: PlaylistTrackSort) { submitSave(scope, sort).await().getOrThrow() }
    suspend fun reset(scope: PlaylistSortScope) { submitReset(scope).await().getOrThrow() }

    private fun enqueue(scope: PlaylistSortScope, sort: PlaylistTrackSort, reset: Boolean): Deferred<Result<Unit>> =
        synchronized(acceptanceLock) {
            check(writerScope.isActive) { "排序设置存储已关闭" }
            val change = Accepted(++revision, PlaylistSortStorageState(sort, saving = true))
            accepted.update { it + (scope to change) }
            writerScope.async {
                writeMutex.withLock {
                    if (accepted.value[scope]?.revision != change.revision) return@withLock Result.success(Unit)
                    try {
                        if (reset) store.edit { it.remove(key(scope)) } else write(scope, sort)
                        finish(scope, change, error = null)
                        Result.success(Unit)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        finish(scope, change, "排序保存失败，请重新选择排序：${error.message.orEmpty()}")
                        Result.failure(error)
                    }
                }
            }
        }

    private fun finish(scope: PlaylistSortScope, change: Accepted, error: String?) {
        accepted.update { values ->
            if (values[scope]?.revision != change.revision) values
            else values + (scope to change.copy(value = change.value.copy(saving = false, error = error)))
        }
    }

    private suspend fun write(scope: PlaylistSortScope, sort: PlaylistTrackSort) {
        store.edit { values ->
            // A newer format or unreadable value must not be silently overwritten by a downgrade.
            decode(values[key(scope)])
            if (sort == PlaylistTrackSort()) values.remove(key(scope)) else values[key(scope)] = encode(sort)
        }
    }

    override fun close() {
        synchronized(acceptanceLock) {
            writerScope.cancel()
            accepted.value = emptyMap()
        }
    }

    private fun encode(sort: PlaylistTrackSort): String {
        val option = when (sort.option) {
            PlaylistSortOption.DEFAULT -> "original"
            PlaylistSortOption.TITLE -> "title"
            PlaylistSortOption.ARTIST -> "artist"
            PlaylistSortOption.DURATION -> "duration"
        }
        return "1|$option|${if (sort.reversed) 1 else 0}"
    }

    private fun decode(raw: String?): PlaylistTrackSort {
        if (raw == null) return PlaylistTrackSort()
        val parts = raw.split('|')
        if (parts.size != 3 || parts[0] != "1") throw IOException("排序设置版本无法识别，原设置已保留")
        val option = when (parts[1]) {
            "original" -> PlaylistSortOption.DEFAULT
            "title" -> PlaylistSortOption.TITLE
            "artist" -> PlaylistSortOption.ARTIST
            "duration" -> PlaylistSortOption.DURATION
            else -> throw IOException("排序方式无法识别，原设置已保留")
        }
        val reversed = when (parts[2]) {
            "0" -> false
            "1" -> true
            else -> throw IOException("排序方向无法识别，原设置已保留")
        }
        return PlaylistTrackSort(option, reversed)
    }
}
