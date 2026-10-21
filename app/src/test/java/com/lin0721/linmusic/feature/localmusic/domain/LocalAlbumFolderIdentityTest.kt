package com.lin0721.linmusic.feature.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAlbumFolderIdentityTest {
    private val authority = "content://com.android.externalstorage.documents"
    private val prefix = "saf://com.android.externalstorage.documents/primary"

    @Test
    fun `real paths take priority and retain existing folder identity`() {
        assertEquals("/storage/emulated/0/Music/Album", localAlbumFolderOf(
            "/storage/emulated/0/Music/Album/song.flac", "$authority/document/primary%3AOther%2Fsong.flac"))
        assertEquals("/storage/emulated/0/Music/Album", localAlbumFolderOf(
            "/storage/emulated/0/Music/Album/song.flac", "not a URI"))
    }

    @Test
    fun `tree document and individual document agree on the parent directory`() {
        val id = "primary%3AMusic%2F50%20Classical%20Masterworks%2Fsong.flac"
        val single = localAlbumFolderOf(null, "$authority/document/$id")
        val tree = localAlbumFolderOf(null, "$authority/tree/primary%3AMusic/document/$id")
        assertEquals("$prefix/Music/50 Classical Masterworks", single)
        assertEquals(single, tree)
    }

    @Test
    fun `different filenames in one album have the same identity`() {
        assertEquals(localAlbumFolderOf(null, "$authority/document/primary%3AMusic%2FAlbum%2F01.flac"),
            localAlbumFolderOf(null, "$authority/tree/primary%3AMusic%2FAlbum/document/primary%3AMusic%2FAlbum%2F02.flac"))
    }

    @Test
    fun `Chinese plus and literal percent sequences are decoded once`() {
        assertEquals("$prefix/Music/中文+ A%2FB", localAlbumFolderOf(null,
            "$authority/document/primary%3AMusic%2F%E4%B8%AD%E6%96%87+%20A%252FB%2F01.flac"))
        assertEquals("$prefix/Music/50% Classical", localAlbumFolderOf(null,
            "$authority/document/primary%3AMusic%2F50%25%20Classical%2F01.flac"))
    }

    @Test
    fun `different volumes and directories do not merge`() {
        val first = localAlbumFolderOf(null, "$authority/document/primary%3AMusic%2FAlbum%2F01.flac")
        assertNotEquals(first, localAlbumFolderOf(null, "$authority/document/1234-5678%3AMusic%2FAlbum%2F01.flac"))
        assertNotEquals(first, localAlbumFolderOf(null, "$authority/document/primary%3AOther%2FAlbum%2F01.flac"))
    }

    @Test
    fun `opaque and untrusted providers supply no album directory evidence`() {
        listOf(null, "", "not a URI", "content://media/external/audio/media/123",
            "content://com.example.cloud/document/primary%3AMusic%2FAlbum%2F01.flac",
            "https://com.android.externalstorage.documents/document/primary%3AMusic%2FAlbum%2F01.flac",
            "content://user@com.android.externalstorage.documents/document/primary%3AMusic%2FAlbum%2F01.flac",
            "content://com.android.externalstorage.documents:80/document/primary%3AMusic%2FAlbum%2F01.flac"
        ).forEach { assertNull(it, localAlbumFolderOf(null, it)) }
    }

    @Test
    fun `tree grants volume roots and malformed document paths stay ungrouped`() {
        listOf("/tree/primary%3AMusic", "/document/primary%3A01.flac", "/document/primary%3A",
            "/document/primary%3AMusic%2FAlbum%2F", "/document/primary%3AMusic%2F..%2F01.flac",
            "/document/primary%3AMusic%2F%2F01.flac", "/document/primary%3AMusic%2F%00%2F01.flac",
            "/document/primary%3AMusic%2F%FF%2F01.flac", "/document/primary%3AMusic%2FAlbum%2F01%Q0.flac",
            "/document/no-volume%2FMusic%2F01.flac", "/document/%3AMusic%2F01.flac",
            "/document/primary%3AMusic%2FAlbum%2F01.flac/extra"
        ).forEach { assertNull(it, localAlbumFolderOf(null, "$authority$it")) }
    }
}
