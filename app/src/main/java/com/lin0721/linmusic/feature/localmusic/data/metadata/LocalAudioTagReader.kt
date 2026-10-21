package com.lin0721.linmusic.feature.localmusic.data.metadata

import android.content.Context
import android.net.Uri
import com.kyant.taglib.TagLib
import com.lin0721.linmusic.core.log.AppLogger

private const val TAG = "LocalAudioTagReader"

/** Native tag reads use existing read grants and never request write access or edit the audio. */
internal fun readLocalAudioTags(context: Context, uri: Uri): LocalAudioMetadata? =
    readTagLib(context, uri, pictures = false) { properties, _ -> localAudioMetadataFromProperties(properties) }

internal fun readLocalAudioPicture(context: Context, uri: Uri): ByteArray? =
    readTagLib(context, uri, pictures = true) { _, pictures -> pictures.firstOrNull { it.isNotEmpty() } }

private fun <T> readTagLib(
    context: Context, uri: Uri, pictures: Boolean,
    map: (Map<String, Array<String>>, List<ByteArray>) -> T?
): T? = try {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        // TagLib owns and closes the detached duplicate; the original descriptor stays scoped here.
        val metadata = TagLib.getMetadata(descriptor.dup().detachFd(), readPictures = pictures) ?: return@use null
        map(metadata.propertyMap, if (pictures) metadata.pictures.map { it.data } else emptyList())
    }
} catch (error: Exception) {
    AppLogger.w(TAG, "读取嵌入标签失败 uri=$uri", error)
    null
} catch (error: LinkageError) {
    // Missing/unsupported native libraries must not turn one fallback into a failed library scan.
    AppLogger.w(TAG, "本机标签读取不可用 uri=$uri", error)
    null
}
