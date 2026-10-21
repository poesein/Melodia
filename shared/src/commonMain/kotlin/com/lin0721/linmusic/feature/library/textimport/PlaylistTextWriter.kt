package com.lin0721.linmusic.feature.library.textimport

import kotlinx.coroutines.CancellationException

/** One destination per import session; retries reconcile it before adding missing IDs. */
class PlaylistTextWriter {
    var playlistId: Long? = null
        private set
    private var expectedIds: List<Long>? = null
    private var completed = false
    private var existingTarget: Long? = null

    suspend fun write(
        ids: List<Long>,
        create: suspend () -> Long,
        add: suspend (Long, List<Long>) -> Unit,
        readIds: suspend (Long) -> Set<Long>,
        existingPlaylistId: Long? = null
    ): Long {
        val unique = ids.filter { it > 0 }.distinct()
        require(unique.isNotEmpty()) { "没有可导入的歌曲" }
        require(unique.size <= PlaylistTextDocument.MAX_ROWS)
        require(existingPlaylistId == null || existingPlaylistId > 0)
        require(expectedIds == null || expectedIds == unique) { "导入已开始，重试时不能更换导入内容" }
        require(playlistId == null || existingTarget == existingPlaylistId) { "导入已开始，不能更换目标歌单" }
        val createdNow = playlistId == null && existingPlaylistId == null
        val id = playlistId ?: (existingPlaylistId ?: create()).also {
            require(it > 0) { "未返回有效歌单 ID" }
            playlistId = it
            existingTarget = existingPlaylistId
            expectedIds = unique
        }
        if (completed) return id
        // On retry, reconcile a previous response that may have been lost after the server applied it.
        // Only a newly created playlist is empty. Existing targets need a fresh read even on the
        // first attempt. On retry, a failed read must stop the write: guessing that it
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
