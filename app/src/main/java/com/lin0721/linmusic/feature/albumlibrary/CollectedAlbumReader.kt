package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.network.AppError
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException

/** One account/session owns this reader. Date lookup and playback share its request gate. */
internal class CollectedAlbumReader(
    private val fetch: suspend (Long) -> PlaylistDetail,
    private val peek: suspend (Long) -> PlaylistDetail? = { null },
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    private val gate = Mutex()
    private val completed = ConcurrentHashMap<Long, PlaylistDetail>()
    private var requested = false

    fun fork(): CollectedAlbumReader = CollectedAlbumReader(fetch, peek, pause).also {
        it.completed.putAll(completed)
        it.requested = requested
    }

    suspend fun read(id: Long, requireDate: Boolean = false): PlaylistDetail = gate.withLock {
        fun reusable(detail: PlaylistDetail): Boolean = detail.id == id && detail.tracks.isNotEmpty() &&
            detail.tracks.all { it.id > 0L } && detail.trackIds.all { declared -> detail.tracks.any { it.id == declared.id } } &&
            (!requireDate || detail.publishTime != 0L)
        completed[id]?.takeIf(::reusable)?.let { return@withLock it }
        peek(id)?.takeIf(::reusable)?.let { completed[id] = it; return@withLock it }
        var failure: Exception? = null
        repeat(3) { attempt ->
            currentCoroutineContext().ensureActive()
            if (requested) pause(if (attempt == 0) 450L else 1_000L * attempt)
            requested = true
            try {
                val detail = withTimeout(12_000) { fetch(id) }
                check(detail.id == id) { "专辑标识不一致" }
                if (reusable(detail)) completed[id] = detail
                return@withLock detail
            } catch (timeout: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                failure = timeout
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (!retryable(error)) throw error
                failure = error
            }
        }
        throw checkNotNull(failure)
    }

    private fun retryable(error: Exception): Boolean = when (error) {
        AppError.NetworkError, AppError.RiskControl -> true
        is IOException -> true
        is HttpException -> error.code() in setOf(408, 429) || error.code() >= 500
        is AppError.BizError -> error.code < 0 || error.code in setOf(408, 429) || error.code >= 500
        else -> false
    }
}

internal class CollectedAlbumReadFailure(val albumName: String, cause: Exception) : Exception(cause)
