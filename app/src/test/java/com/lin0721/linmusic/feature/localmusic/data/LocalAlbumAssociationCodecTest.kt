package com.lin0721.linmusic.feature.localmusic.data

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.SongPrivilege
import com.lin0721.linmusic.core.model.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LocalAlbumAssociationCodecTest {
    private val artist = Artist(15L, "U2", "artist-cover", "artist-image")
    private val album = Album(20L, "The Joshua Tree", "album-cover", listOf(artist), 542_851_200_000L)
    private fun binding(key: String = "local/album:一张专辑") = LocalAlbumAssociation(
        localAlbumKey = key,
        album = album,
        tracks = listOf(
            Track(31L, "Where the Streets Have No Name", listOf(artist), album, 1, 542_851_200_000L,
                337_000L, SongPrivilege(dlLevel = "lossless", plLevel = "exhigh", flLevel = "standard", maxBrLevel = "lossless")),
            Track(32L, "I Still Haven't Found What I'm Looking For", listOf(artist), album, dt = 277_000L),
        ),
    )

    private fun entry(binding: LocalAlbumAssociation): Pair<String, String> =
        LocalAlbumAssociationCodec.keyFor(binding.localAlbumKey) to LocalAlbumAssociationCodec.encode(binding)

    @Test
    fun `roundtrip preserves original album key and complete online track metadata`() {
        val original = binding()
        assertEquals(mapOf(original.localAlbumKey to original), LocalAlbumAssociationCodec.decodeEntries(mapOf(entry(original))))
    }

    @Test
    fun `one malformed record does not hide valid cached album`() {
        val valid = binding()
        val entries = mapOf(entry(valid), LocalAlbumAssociationCodec.keyFor("broken") to "{broken json")
        assertEquals(mapOf(valid.localAlbumKey to valid), LocalAlbumAssociationCodec.decodeEntries(entries))
    }

    @Test
    fun `unknown future fields do not invalidate an album cache`() {
        val original = binding()
        val encoded = LocalAlbumAssociationCodec.encode(original).replaceFirst("{", "{\"futureOption\":{\"enabled\":true},")
        assertEquals(mapOf(original.localAlbumKey to original), LocalAlbumAssociationCodec.decodeEntries(
            mapOf(LocalAlbumAssociationCodec.keyFor(original.localAlbumKey) to encoded)))
    }

    @Test
    fun `mismatched payload key and unrelated preference cannot inject binding`() {
        val original = binding()
        val encoded = LocalAlbumAssociationCodec.encode(original)
        assertTrue(LocalAlbumAssociationCodec.decodeEntries(mapOf(
            LocalAlbumAssociationCodec.keyFor("another local album") to encoded,
            "song_uri_31" to encoded,
        )).isEmpty())
    }

    @Test
    fun `album keys containing punctuation and unicode remain distinct and safe`() {
        val keys = listOf("folder/album", "folder\\album", "folder:album", "a\"},{\"album_b", "专辑 🎵", "\ud800", "\ud801")
        val encoded = keys.map(LocalAlbumAssociationCodec::keyFor)
        assertEquals(keys.size, encoded.toSet().size)
        assertTrue(encoded.all { it.matches(Regex("album_[0-9a-f]+")) })
        assertNotEquals(LocalAlbumAssociationCodec.keyFor("a"), LocalAlbumAssociationCodec.keyFor("a "))
    }

    @Test
    fun `invalid individual binding is skipped without discarding another album`() {
        val valid = binding()
        val invalid = binding("invalid").copy(tracks = listOf(Track(id = 0L)))
        val entries = mapOf(entry(valid), LocalAlbumAssociationCodec.keyFor(invalid.localAlbumKey) to Json.encodeToString(invalid))
        assertEquals(mapOf(valid.localAlbumKey to valid), LocalAlbumAssociationCodec.decodeEntries(entries))
    }

    @Test
    fun `encoder rejects incomplete or ambiguous caches before persistence`() {
        val original = binding()
        val invalid = listOf(
            original.copy(localAlbumKey = " "),
            original.copy(album = Album(id = 0L)),
            original.copy(tracks = emptyList()),
            original.copy(tracks = listOf(Track(id = -1L))),
            original.copy(tracks = listOf(original.tracks.first(), original.tracks.first())),
        )
        invalid.forEach { value ->
            try {
                LocalAlbumAssociationCodec.encode(value)
                fail("Invalid album binding must not be persisted")
            } catch (_: IllegalArgumentException) {
                // Rejected before entering DataStore.edit.
            }
        }
    }

}
