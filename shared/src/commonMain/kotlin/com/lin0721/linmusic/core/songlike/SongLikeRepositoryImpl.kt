package com.lin0721.linmusic.core.songlike

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.apiFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

/** One acknowledged red-heart state, shared by all pages and notification controls. */
class SongLikeRepositoryImpl(
    private val apiService: SongLikeApi,
    private val userPreferences: UserPreferences? = null,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val pause: suspend (Long) -> Unit = { delay(it) }
) : SongLikeRepository {
    private val _likedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    override val likedSongIds: StateFlow<Set<Long>> = _likedSongIds.asStateFlow()
    private val stateLock = Any()
    private val writeGate = Mutex()
    private var account: Long? = null
    private var generation = 0L
    private data class Change(val liked: Boolean, val at: Long)
    private val recentChanges = mutableMapOf<Long, Change>()
    private var nextWriteAt = 0L
    private var cooldownUntil = 0L
    private var cooldownError: Throwable = AppError.RiskControl

    override fun getLikedSongIds(uid: Long): Flow<Result<List<Long>>> = flow {
        if (userPreferences != null && userPreferences.userProfile.first()?.uid != uid) throw CancellationException("登录账户已改变")
        val context = synchronized(stateLock) {
            if (account != null && account != uid) {
                generation++; recentChanges.clear(); _likedSongIds.value = emptySet()
            }
            account = uid
            generation
        }
        val response = apiFlow(request = { apiService.getLikedSongIds(LikeSongListRequest(uid)) },
            isSuccess = { it.isSuccess }, code = { it.code }, transform = { it.ids }).first()
        if (userPreferences != null && userPreferences.userProfile.first()?.uid != uid) throw CancellationException("登录账户已改变")
        val result = response.map { ids -> synchronized(stateLock) {
            if (generation != context || account != uid) throw CancellationException("登录账户已改变")
            val merged = ids.toMutableSet()
            val changes = recentChanges.iterator()
            while (changes.hasNext()) {
                val (id, change) = changes.next()
                if (now() - change.at > 120_000L) changes.remove()
                else if (change.liked) merged.add(id) else merged.remove(id)
            }
            _likedSongIds.value = merged
            merged.toList()
        } }
        emit(result)
    }

    override fun likeSong(songId: Long, like: Boolean): Flow<Result<Unit>> = flow {
        require(songId > 0L) { "歌曲标识无效" }
        val uid = userPreferences?.userProfile?.first()?.uid
        if (userPreferences != null && uid == null) { emit(Result.failure(AppError.Unauthorized)); return@flow }
        val context = synchronized(stateLock) { generation }
        val result = writeGate.withLock {
            suspend fun checkAccount() {
                currentCoroutineContext().ensureActive()
                if (synchronized(stateLock) { generation != context } ||
                    (userPreferences != null && userPreferences.userProfile.first()?.uid != uid)) {
                    throw CancellationException("登录账户已改变")
                }
            }
            checkAccount()
            if (now() < cooldownUntil) return@withLock Result.failure<Unit>(cooldownError)
            var last: Result<Unit> = Result.failure(AppError.NetworkError)
            for (attempt in 0..2) {
                pause((nextWriteAt - now()).coerceAtLeast(0L))
                checkAccount()
                nextWriteAt = now() + 800L
                last = try {
                    withTimeout(15_000) {
                        apiFlow(request = { apiService.likeSong(LikeSongRequest(songId, like)) },
                            isSuccess = { it.isSuccess }, code = { it.code }, msg = { it.message }, transform = { Unit }).first()
                    }
                } catch (timeout: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive(); Result.failure(AppError.NetworkError)
                }
                checkAccount()
                if (last.isSuccess) {
                    synchronized(stateLock) {
                        if (generation != context) throw CancellationException("登录账户已改变")
                        _likedSongIds.value = if (like) _likedSongIds.value + songId else _likedSongIds.value - songId
                        recentChanges[songId] = Change(like, now())
                    }
                    break
                }
                val error = last.exceptionOrNull()!!
                if (!retryable(error)) break
                if (attempt < 2) pause(2_000L * (attempt + 1))
                else {
                    cooldownError = if (rateLimited(error)) AppError.RiskControl else error
                    cooldownUntil = now() + 10_000L
                    last = Result.failure(cooldownError)
                }
            }
            last
        }
        // Emit outside the gate: first() cancellation must never undo an acknowledged write.
        emit(result)
    }

    private fun rateLimited(error: Throwable): Boolean = error == AppError.RiskControl ||
        (error is AppError.BizError && error.code in setOf(429, 460, 461, 462, -460, -461, -462)) ||
        (error is HttpException && error.code() == 429)
    private fun retryable(error: Throwable) = rateLimited(error) || error == AppError.NetworkError ||
        (error is AppError.BizError && error.code in 500..599) || (error is HttpException && error.code() in 500..599)

    override fun syncLikedSongIds(ids: Set<Long>) = synchronized(stateLock) {
        if (ids != _likedSongIds.value) {
            generation++; recentChanges.clear(); account = null
            _likedSongIds.value = ids
        }
    }
}
