package com.lin0721.linmusic.feature.library.textimport

import com.lin0721.linmusic.core.model.Track
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale

enum class TextPlaylistStatus { PENDING, MATCHED, REVIEW, NOT_FOUND, ERROR, INVALID, DUPLICATE }

data class TextPlaylistRow(
    val line: Int,
    val original: String,
    val title: String = "",
    val artist: String = "",
    val status: TextPlaylistStatus = TextPlaylistStatus.PENDING,
    val candidates: List<Track> = emptyList(),
    val selected: Track? = null,
    val included: Boolean = true,
    val message: String? = null
)

/** TXT is data only. Bound bytes/rows before searching; retain source line order and invalid rows for review. */
object PlaylistTextDocument {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_ROWS = 500
    private val separator = Regex("\\s+[-–—－]\\s+|\\t+")
    private val compactSeparator = Regex("[-–—－]")

    private fun fields(value: String): List<Pair<String, String>> {
        // Prefer explicit separators so names such as Jay-Z and AC-DC stay intact.
        // For compact text, keep all possible splits until online metadata can disambiguate them.
        val explicit = separator.findAll(value).toList()
        return (explicit.ifEmpty { compactSeparator.findAll(value).toList() }).map { split ->
            value.substring(0, split.range.first).trim() to value.substring(split.range.last + 1).trim()
        }
    }

    fun decode(bytes: ByteArray): String {
        require(bytes.size <= MAX_BYTES) { "TXT 文件不能超过 1 MiB" }
        require(bytes.isNotEmpty()) { "文件为空" }
        val (offset, charset) = when {
            bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte() -> 3 to Charsets.UTF_8
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> 2 to Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> 2 to Charsets.UTF_16BE
            else -> 0 to Charsets.UTF_8
        }
        fun read(using: Charset): String = using.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        val text = try { read(charset) } catch (e: java.nio.charset.CharacterCodingException) {
            if (offset != 0) throw IllegalArgumentException("TXT 编码损坏", e)
            try { read(Charset.forName("GB18030")) } catch (e: java.nio.charset.CharacterCodingException) {
                throw IllegalArgumentException("无法读取 TXT 编码，请另存为 UTF-8", e)
            }
        }
        require('\u0000' !in text) { "文件不是有效的文本，请另存为 UTF-8" }
        return text
    }

    fun parse(text: String): List<TextPlaylistRow> {
        require(text.length <= MAX_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "导入文本不能超过 1 MiB" }
        require('\u0000' !in text) { "内容不是有效的文本" }
        val lines = text.removePrefix("\uFEFF").lineSequence().withIndex().filter { it.value.isNotBlank() }.take(MAX_ROWS + 1).toList()
        require(lines.isNotEmpty()) { "内容中没有歌曲" }
        require(lines.size <= MAX_ROWS) { "每次最多导入 $MAX_ROWS 行歌曲" }
        val seen = mutableSetOf<Pair<String, String>>()
        return lines.map { (index, raw) ->
            val value = raw.trim()
            val (title, artist) = if (value.length <= 1000) fields(value).lastOrNull() ?: ("" to "") else "" to ""
            when {
                identity(title).isBlank() || identity(artist).isBlank() || value.length > 1000 -> TextPlaylistRow(index + 1, value,
                    status = TextPlaylistStatus.INVALID, included = false, message = "每行填写：歌名 - 歌手，或歌手 - 歌名")
                !seen.add(identity(title) to identity(artist)) -> TextPlaylistRow(index + 1, value, title, artist,
                    TextPlaylistStatus.DUPLICATE, included = false, message = "重复行，已跳过")
                else -> TextPlaylistRow(index + 1, value, title, artist)
            }
        }
    }

    fun identity(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        .replace(Regex("[\\p{P}\\p{Z}\\s]+"), "")

    fun resolve(row: TextPlaylistRow, candidates: List<Track>): TextPlaylistRow {
        val valid = candidates.filter { it.id > 0 }.distinctBy { it.id }
        // Same title/artist editions may differ in album. Prefer the service's highest-ranked exact entry;
        // title version words are retained, so Live/Remix never silently replace an unqualified title.
        val interpretations = fields(row.original).ifEmpty { listOf(row.title to row.artist) }
            .flatMap { (left, right) -> listOf(left to right, right to left) }
            .filter { (title, artist) -> identity(title).isNotBlank() && identity(artist).isNotBlank() }
            .distinctBy { (title, artist) -> identity(title) to identity(artist) }
        val matches = interpretations.mapNotNull { (title, artist) ->
            valid.firstOrNull { track ->
                identity(track.name) == identity(title) && track.ar.any { identity(it.name) == identity(artist) }
            }?.let { Triple(title, artist, it) }
        }.distinctBy { it.third.id }
        // Swapped fields or an internal hyphen can describe different real songs. Do not guess.
        val exact = matches.singleOrNull()
        val preview = (matches.map { it.third } + valid).distinctBy { it.id }.take(8)
        return row.copy(title = exact?.first ?: row.title, artist = exact?.second ?: row.artist,
            candidates = preview, selected = exact?.third,
            status = if (exact != null) TextPlaylistStatus.MATCHED else if (valid.isEmpty()) TextPlaylistStatus.NOT_FOUND else TextPlaylistStatus.REVIEW,
            message = when {
                exact != null -> null
                valid.isEmpty() -> "未找到歌曲，可重新搜索"
                matches.size > 1 -> "歌名、歌手或分隔位置存在歧义，请点选正确候选"
                else -> "没有歌名与歌手都一致的条目，请点选候选"
            })
    }

    fun selectedIds(rows: List<TextPlaylistRow>): List<Long> = rows.filter { it.included }
        .mapNotNull { it.selected?.id?.takeIf { id -> id > 0 } }.distinct()
}
