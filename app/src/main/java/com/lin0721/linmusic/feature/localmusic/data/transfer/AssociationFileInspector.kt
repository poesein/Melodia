package com.lin0721.linmusic.feature.localmusic.data.transfer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import com.lin0721.linmusic.feature.localmusic.data.localAudioIdentity
import com.lin0721.linmusic.feature.localmusic.data.scan.resolveArtistAndTitle
import com.lin0721.linmusic.feature.localmusic.data.metadata.*
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import kotlinx.coroutines.CancellationException

/** Reads a currently granted audio reference. It never acquires/relinquishes permissions. */
internal fun inspectAssociationFile(context: Context, uriString: String, fallback: LocalTrack? = null): AssociationLocalCandidate? {
    val uri = Uri.parse(uriString)
    if (uri.scheme != "content") return null
    return try {
        var fileName: String? = fallback?.path?.substringAfterLast('/')
        var queriedSize: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) fileName = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) queriedSize = cursor.getLong(sizeIndex)
                }
            }
        }
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { asset ->
            val stat = runCatching { Os.fstat(asset.parcelFileDescriptor.fileDescriptor) }.getOrNull()
            val regular = stat != null && OsConstants.S_ISREG(stat.st_mode)
            val size = asset.declaredLength.takeIf { it > 0L }
                ?: (if (regular) (stat!!.st_size - asset.startOffset).takeIf { it > 0L } else queriedSize?.takeIf { it > 0L })
                ?: return@use null
            val metadata = MediaMetadataRetriever()
            val local: AssociationLocalCandidate
            try {
                metadata.setDataSource(asset.parcelFileDescriptor.fileDescriptor, asset.startOffset, size)
                val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L }
                    ?: return@use null
                val system = LocalAudioMetadata(
                    title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                val wave = isWaveAudio(fileName, runCatching { context.contentResolver.getType(uri) }.getOrNull())
                val tags = mergeLocalAudioMetadata(system,
                    if (needsEmbeddedAudioTags(system, wave, extendedFields = false)) readLocalAudioTags(context, uri) else null, wave)
                val rawTitle = tags.title
                    ?: fileName?.substringBeforeLast('.')?.trim().orEmpty()
                val rawArtist = tags.artist.orEmpty()
                val (artist, title) = resolveArtistAndTitle(rawArtist, rawTitle)
                val album = tags.album
                val identity = localAudioIdentity(context, uriString) ?: return@use null
                if (regular) {
                    // A provider/path replacement between the descriptor and identity reads is unresolved.
                    val fileStat = stat!!
                    val descriptorIdentity = "file:${fileStat.st_dev}:${fileStat.st_ino}:${asset.startOffset}:$size"
                    if (identity != descriptorIdentity) return@use null
                }
                local = AssociationLocalCandidate(uriString, identity, fileName, title, artist, album, duration, size)
            } finally { metadata.release() }
            local
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}
