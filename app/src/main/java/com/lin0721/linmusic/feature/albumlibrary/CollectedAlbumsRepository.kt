package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.core.cache.OfflineFallback
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.network.mapToAppError
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import com.lin0721.linmusic.feature.library.data.AlbumSublistRequest
import com.lin0721.linmusic.feature.library.data.LibraryApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** A separate reader keeps complete album pagination out of the upstream library contract. */
class CollectedAlbumsRepository internal constructor(
    private val source: (Long) -> Flow<Result<List<AlbumSubItem>>>
) {
    constructor(api: LibraryApi, offlineFallback: OfflineFallback) : this({ uid ->
        // Include the captured account in the key even though MetadataCache also has UID dirs.
        // A response finishing during account switching must never be a cache hit for its successor.
        offlineFallback.cached<List<AlbumSubItem>>("personal_collected_albums_v1_$uid") {
            flow {
                try {
                    val albums = linkedMapOf<Long, AlbumSubItem>()
                    var offset = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val page = apiFlow(
                            request = { api.getAlbumSublist(AlbumSublistRequest(limit = PAGE_SIZE, offset = offset)) },
                            isSuccess = { it.isSuccess }, code = { it.code }, transform = { it.data }
                        ).first().getOrThrow()
                        currentCoroutineContext().ensureActive()
                        val before = albums.size
                        page.filter { it.id > 0L }.forEach { albums.putIfAbsent(it.id, it) }
                        // Never cache/publish a partial first page as a complete collection.
                        if (page.isNotEmpty() && albums.size == before) {
                            error("收藏专辑分页没有返回新条目，请重试")
                        }
                        if (page.size < PAGE_SIZE) break
                        val nextOffset = offset.toLong() + page.size
                        check(nextOffset <= Int.MAX_VALUE) { "收藏专辑分页位置异常，请重试" }
                        offset = nextOffset.toInt()
                    }
                    emit(Result.success(albums.values.toList()))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    emit(Result.failure(mapToAppError(error)))
                }
            }
        }
    })

    fun load(uid: Long): Flow<Result<List<AlbumSubItem>>> =
        if (uid > 0L) source(uid) else flow { emit(Result.failure(AppError.Unauthorized)) }

    fun observe(uid: Long): Flow<Result<List<AlbumSubItem>>> = load(uid)

    private companion object { const val PAGE_SIZE = 200 }
}
