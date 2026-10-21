package com.lin0721.linmusic.feature.library.textimport

import kotlinx.coroutines.CancellationException

/** One new playlist per import session; retries reuse its ID, including after ambiguous add responses. */
class PlaylistTextWriter {
    var playlistId: Long? = null
        private set
    private var expectedIds: List<Long>? = null
    private var completed = false

    suspend fun write(
        ids: List<Long>,
        create: suspend () -> Long,
        add: suspend (Long, List<Long>) -> Unit,
        readIds: suspend (Long) -> Set<Long>
    ): Long {
        val unique = ids.filter { it > 0 }.distinct()
        require(unique.isNotEmpty()) { "没有可导入的歌曲" }
        require(unique.size <= PlaylistTextDocument.MAX_ROWS)
        require(expectedIds == null || expectedIds == unique) { "已创建歌单，重试时不能更换导入内容" }
        val createdNow = playlistId == null
        val id = playlistId ?: create().also {
            require(it > 0) { "创建歌单未返回有效 ID" }
            playlistId = it
            expectedIds = unique
        }
        if (completed) return id
        // On retry, reconcile a previous response that may have been lost after the server applied it.
        // A fresh playlist is empty. On retry, a failed read must stop the write: guessing that it
        // is empty could repeat a request that the server already applied.
        val present = if (createdNow) emptySet() else readIds(id)
        val missing = unique.filterNot { it in present }
        if (missing.isNotEmpty()) {
            try { add(id, missing) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val applied = try { readIds(id).containsAll(unique) } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                if (!applied) throw error
            }
        }
        completed = true
        return id
    }
}
