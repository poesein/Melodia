package com.lin0721.linmusic.feature.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAlbumGroupingTest {
    private val title = "50 Classical Masterworks"
    private val folder = "/storage/emulated/0/Music/$title"

    private fun entry(artist: String, album: String = title, path: String? = folder, albumArtist: String? = null) =
        LocalAlbumGroupMetadata(album, artist, albumArtist, path)

    @Test
    fun `fifty tracks by different performers in an album directory form one compilation`() {
        val tracks = List(50) { entry("Performer ${it % 36}") }
        val groups = resolveLocalAlbumGroups(tracks)

        assertEquals(50, groups.size)
        assertEquals(1, groups.map { it.key }.distinct().size)
        assertTrue(groups.first().key.startsWith("compilation:"))
        assertEquals(title, groups.first().name)
        assertEquals("Various Artists", groups.first().owner)
        assertEquals(36, tracks.map { it.artist }.distinct().size)
        assertTrue(tracks.all { it.albumArtist == null })
    }

    @Test
    fun `same titles in a flat music folder keep different artist identities`() {
        val groups = resolveLocalAlbumGroups(listOf(
            entry("Artist A", "Greatest Hits", "/sdcard/Music"),
            entry("Artist B", "Greatest Hits", "/sdcard/Music")
        ))

        assertEquals(listOf("Greatest Hits|Artist A", "Greatest Hits|Artist B"), groups.map { it.key })
    }

    @Test
    fun `compilations with the same title in different directories stay separate`() {
        val groups = resolveLocalAlbumGroups(listOf(
            entry("Artist A", path = "/sdcard/Library A/$title"),
            entry("Artist B", path = "/sdcard/Library A/$title"),
            entry("Artist A", path = "/sdcard/Library B/$title"),
            entry("Artist B", path = "/sdcard/Library B/$title")
        ))

        assertEquals(groups[0].key, groups[1].key)
        assertEquals(groups[2].key, groups[3].key)
        assertNotEquals(groups[0].key, groups[2].key)
    }

    @Test
    fun `explicit album artist keeps the existing key and takes priority over track performers`() {
        val groups = resolveLocalAlbumGroups(listOf(
            entry("Artist A", albumArtist = "Curated Ensemble"),
            entry("Artist B", albumArtist = "Curated Ensemble")
        ))

        assertEquals(listOf("$title|Curated Ensemble", "$title|Curated Ensemble"), groups.map { it.key })
        assertEquals("Curated Ensemble", groups.first().owner)
    }

    @Test
    fun `one explicit album artist prevents inference over conflicting directory metadata`() {
        val groups = resolveLocalAlbumGroups(listOf(
            entry("Artist A", albumArtist = "Curated Ensemble"), entry("Artist B")
        ))

        assertEquals(listOf("$title|Curated Ensemble", "$title|Artist B"), groups.map { it.key })
    }

    @Test
    fun `single artist album retains its existing key even in a same named directory`() {
        val groups = resolveLocalAlbumGroups(List(10) { entry("Artist A") })

        assertTrue(groups.all { it.key == "$title|Artist A" && it.owner == "Artist A" })
    }

    @Test
    fun `mixed album tags prevent a directory being inferred as one compilation`() {
        val groups = resolveLocalAlbumGroups(listOf(entry("Artist A"), entry("Artist B", album = "Another Album")))

        assertEquals(listOf("$title|Artist A", "Another Album|Artist B"), groups.map { it.key })
    }

    @Test
    fun `unknown album metadata is never promoted from its directory name`() {
        val groups = resolveLocalAlbumGroups(listOf(entry("Artist A", album = ""), entry("Artist B", album = "<unknown>")))

        assertTrue(groups.all { it.key == UNKNOWN_ALBUM_KEY && it.name == UNKNOWN_ALBUM_NAME })
    }

    @Test
    fun `group identity survives track order changes and ignores missing performer tags`() {
        val tracks = listOf(entry("Artist A"), entry("Artist B"), entry(""))
        val first = resolveLocalAlbumGroups(tracks)
        val reversed = resolveLocalAlbumGroups(tracks.reversed())

        assertEquals(first.map { it.key }.toSet(), reversed.map { it.key }.toSet())
        assertEquals(1, first.map { it.key }.distinct().size)
        val notEnoughKnownArtists = resolveLocalAlbumGroups(listOf(entry("Artist A"), entry("")))
        assertFalse(notEnoughKnownArtists.any { it.key.startsWith("compilation:") })
    }

    @Test
    fun `missing paths and differently named folders cannot infer compilations`() {
        listOf(null, "/sdcard/Music/Downloads").forEach { path ->
            val groups = resolveLocalAlbumGroups(listOf(entry("Artist A", path = path), entry("Artist B", path = path)))
            assertEquals(listOf("$title|Artist A", "$title|Artist B"), groups.map { it.key })
        }
    }
}
