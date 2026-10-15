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

    @Test
    fun `album directory abbreviations and rip annotations do not split a compilation`() {
        val groups = resolveLocalAlbumGroups(List(50) {
            entry("Performer ${it % 36}", path = "/sdcard/Music/50 Classical [FLAC]")
        })
        assertEquals(1, groups.map { it.key }.distinct().size)
        assertEquals("Various Artists", groups.first().owner)
    }

    @Test
    fun `system album artists copied from performers do not split fifty tracks`() {
        val groups = resolveLocalAlbumGroups(List(50) {
            val artist = "Performer ${it % 36}"
            entry(artist, albumArtist = artist)
        })
        val unlabelled = resolveLocalAlbumGroups(List(50) { entry("Performer ${it % 36}") })
        assertEquals(1, groups.map { it.key }.distinct().size)
        assertEquals(unlabelled.first().key, groups.first().key)
    }

    @Test
    fun `unknown album artist sentinels and partially copied artist fields are missing evidence`() {
        val groups = resolveLocalAlbumGroups(listOf(entry("Artist A", albumArtist = "<unknown>"),
            entry("Artist B", albumArtist = " Artist B "), entry("Artist C")))
        assertEquals(1, groups.map { it.key }.distinct().size)
        assertEquals("Artist A", localAlbumOwner("Artist A", " <UNKNOWN> "))
    }

    @Test
    fun `performer lists copied in a different order are not genuine album artists`() {
        val groups = resolveLocalAlbumGroups(listOf(
            entry("Conductor A / Orchestra A", albumArtist = "Orchestra A / Conductor A"),
            entry("Conductor B / Orchestra B", albumArtist = "Orchestra B / Conductor B")))
        assertEquals(1, groups.map { it.key }.distinct().size)
    }

    @Test
    fun `explicit various artists retains its existing album key`() {
        val groups = resolveLocalAlbumGroups(listOf(entry("Artist A", albumArtist = "Various Artists"),
            entry("Artist B", albumArtist = "Various Artists")))
        assertEquals(listOf("$title|Various Artists", "$title|Various Artists"), groups.map { it.key })
    }

    @Test
    fun `conflicting genuine album artists never infer a directory compilation`() {
        val groups = resolveLocalAlbumGroups(listOf(entry("Artist A", albumArtist = "Album Owner A"),
            entry("Artist B", albumArtist = "Album Owner B")))
        assertEquals(listOf("$title|Album Owner A", "$title|Album Owner B"), groups.map { it.key })
    }

    @Test
    fun `another album in the directory does not split the correctly named compilation`() {
        val groups = resolveLocalAlbumGroups(listOf(entry("Artist A"), entry("Artist B"),
            entry("Artist C", album = "Another Album")))
        assertEquals(groups[0].key, groups[1].key)
        assertNotEquals(groups[0].key, groups[2].key)
    }

    @Test
    fun `imported external storage documents form one album without filesystem paths`() {
        val groups = resolveLocalAlbumGroups(List(50) {
            val id = "primary%3AMusic%2F50%20Classical%20Masterworks%2F${it + 1}.flac"
            entry("Performer ${it % 36}", path = localAlbumFolderOf(null,
                "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/$id"))
        })
        assertEquals(1, groups.map { it.key }.distinct().size)
        assertEquals(title, groups.first().name)
    }

    @Test
    fun `generic collection folders do not merge unrelated same named albums`() {
        listOf("Music", "Downloads", "音乐", "Inbox", "Compilation").forEach { name ->
            val groups = resolveLocalAlbumGroups(listOf(entry("Artist A", path = "/sdcard/$name"),
                entry("Artist B", path = "/sdcard/$name")))
            assertEquals(listOf("$title|Artist A", "$title|Artist B"), groups.map { it.key })
        }
    }

    @Test
    fun `internal unknown artist placeholder is not a genuine album owner`() {
        val groups = resolveLocalAlbumGroups(listOf(entry("Artist A", albumArtist = UNKNOWN_ARTIST_NAME),
            entry("Artist B")))
        assertEquals(1, groups.map { it.key }.distinct().size)
    }

    @Test
    fun `short titles cannot accidentally match arbitrary directory names`() {
        val groups = resolveLocalAlbumGroups(listOf(entry("Artist A", album = "A", path = "/sdcard/Random"),
            entry("Artist B", album = "A", path = "/sdcard/Random")))
        assertEquals(listOf("A|Artist A", "A|Artist B"), groups.map { it.key })
    }
}
