package com.lin0721.linmusic.feature.localmusic.domain

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Read-only grouping evidence, never a filesystem path or a playback URI. SAF imports have no
 * `path`; external-storage document IDs still identify their volume and parent directory.
 * Other providers' IDs are opaque and must not be interpreted as directory paths.
 */
internal fun localAlbumFolderOf(path: String?, uri: String?): String? {
    folderPathOf(path)?.let { return it }
    val document = runCatching { URI(uri ?: return null) }.getOrNull() ?: return null
    if (!document.scheme.equals("content", ignoreCase = true) ||
        document.rawAuthority != "com.android.externalstorage.documents") return null

    val parts = document.rawPath?.takeIf { it.startsWith('/') }?.drop(1)?.split('/') ?: return null
    // A tree grant alone identifies a directory, not the parent of an individual audio file.
    val rawId = when {
        parts.size == 2 && parts[0] == "document" -> parts[1]
        parts.size == 4 && parts[0] == "tree" && parts[1].isNotEmpty() && parts[2] == "document" -> parts[3]
        else -> return null
    }
    // URLDecoder otherwise treats '+' as a space. Decode exactly once: a literal "%2F" in a
    // filename is encoded as "%252F" and must not become a directory separator on a second pass.
    val id = runCatching {
        URLDecoder.decode(rawId.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrNull() ?: return null
    if (id.any { it.isISOControl() || it == '\\' || it == '\uFFFD' }) return null
    val colon = id.indexOf(':').takeIf { it > 0 } ?: return null
    val volume = id.substring(0, colon).takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: return null
    val relative = id.substring(colon + 1).split('/')
    // A file directly in the volume root supplies no album-directory evidence. Empty and dot
    // segments are ambiguous and deliberately do not participate in automatic grouping.
    if (relative.size < 2 || relative.any { it.isEmpty() || it == "." || it == ".." }) return null
    return "saf://com.android.externalstorage.documents/$volume/${relative.dropLast(1).joinToString("/")}"
}
