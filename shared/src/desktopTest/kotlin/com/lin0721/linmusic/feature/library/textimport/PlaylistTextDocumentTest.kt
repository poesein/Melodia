package com.lin0721.linmusic.feature.library.textimport

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class PlaylistTextDocumentTest {
    private fun track(id: Long, name: String = "Starlight", artist: String = "Muse") =
        Track(id = id, name = name, ar = listOf(Artist(name = artist)))

    @Test fun suppliedPlaylistRetainsAll26LinesInOrder() {
        val rows = PlaylistTextDocument.parse(sample)
        assertEquals(26, rows.size)
        assertTrue(rows.all { it.status == TextPlaylistStatus.PENDING })
        assertEquals("Map of the Problematique", rows.first().title)
        assertEquals("Collapse / Collide", rows[16].title)
        assertEquals("Archive", rows[16].artist)
        assertEquals("A Message", rows.last().title)
    }

    @Test fun separatorsAndPhysicalLineNumbersArePreserved() {
        val rows = PlaylistTextDocument.parse("\uFEFF\r\n Starlight - Muse \r\n\r\nAC-DC Song – AC/DC\nA - B - Artist\n标题\t歌手")
        assertEquals(listOf(2, 4, 5, 6), rows.map { it.line })
        assertEquals("AC-DC Song", rows[1].title)
        assertEquals("A - B", rows[2].title)
        assertEquals("歌手", rows.last().artist)
    }

    @Test fun invalidAndDuplicateLinesRemainVisibleButUnselected() {
        val rows = PlaylistTextDocument.parse("Starlight - Muse\n starlight - MUSE\nwrong format\n - Artist\n... - Muse\nStarlight - Other")
        assertEquals(listOf(TextPlaylistStatus.PENDING, TextPlaylistStatus.DUPLICATE, TextPlaylistStatus.INVALID,
            TextPlaylistStatus.INVALID, TextPlaylistStatus.INVALID, TextPlaylistStatus.PENDING), rows.map { it.status })
        assertFalse(rows[1].included)
    }

    @Test fun decodesCommonWindowsTextEncodings() {
        val text = "五味瓶 - 陈绮贞\r\nStarlight - Muse"
        assertEquals(text, PlaylistTextDocument.decode(text.toByteArray()))
        assertEquals(text, PlaylistTextDocument.decode(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + text.toByteArray()))
        assertEquals(text, PlaylistTextDocument.decode(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)))
        assertEquals(text, PlaylistTextDocument.decode(byteArrayOf(0xfe.toByte(), 0xff.toByte()) + text.toByteArray(Charsets.UTF_16BE)))
        assertEquals(text, PlaylistTextDocument.decode(text.toByteArray(Charset.forName("GB18030"))))
    }

    @Test fun rejectsEmptyOversizeTooManyRowsAndBinaryInput() {
        assertThrows(IllegalArgumentException::class.java) { PlaylistTextDocument.parse(" \n ") }
        assertThrows(IllegalArgumentException::class.java) { PlaylistTextDocument.parse((1..501).joinToString("\n") { "$it - Muse" }) }
        assertThrows(IllegalArgumentException::class.java) { PlaylistTextDocument.decode(ByteArray(PlaylistTextDocument.MAX_BYTES + 1)) }
        assertThrows(IllegalArgumentException::class.java) { PlaylistTextDocument.decode("a\u0000b".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { PlaylistTextDocument.decode(byteArrayOf()) }
    }

    @Test fun exactTitleArtistMatchesWithoutAcceptingLiveOrOtherArtists() {
        val row = PlaylistTextDocument.parse("Starlight - Muse").single()
        val result = PlaylistTextDocument.resolve(row, listOf(track(1, "Starlight (Live)"), track(2, artist = "Other"), track(3)))
        assertEquals(3L, result.selected?.id)
        assertEquals(TextPlaylistStatus.MATCHED, result.status)
        assertEquals(3L, result.candidates.first().id)
    }

    @Test fun uncertainResultsRequireManualSelectionAndEmptyIsNotFound() {
        val row = PlaylistTextDocument.parse("Starlight - Muse").single()
        val result = PlaylistTextDocument.resolve(row, listOf(track(1, "Starlight (Live)"), track(0)))
        assertNull(result.selected)
        assertEquals(TextPlaylistStatus.REVIEW, result.status)
        assertEquals(TextPlaylistStatus.NOT_FOUND, PlaylistTextDocument.resolve(row, emptyList()).status)
    }

    @Test fun exactEntryBeyondPreviewLimitRemainsSelectable() {
        val row = PlaylistTextDocument.parse("Starlight - Muse").single()
        val result = PlaylistTextDocument.resolve(row, (1L..12L).map { track(it, "Starlight $it") } + track(20))
        assertEquals(20L, result.selected?.id)
        assertTrue(result.candidates.any { it.id == 20L })
        assertEquals(8, result.candidates.size)
    }

    @Test fun selectedIdsRespectExclusionsAndDeduplicateInFileOrder() {
        val rows = listOf(TextPlaylistRow(1, "", selected = track(7)), TextPlaylistRow(2, "", selected = track(3)),
            TextPlaylistRow(3, "", selected = track(7)), TextPlaylistRow(4, "", selected = track(9), included = false), TextPlaylistRow(5, ""))
        assertEquals(listOf(7L, 3L), PlaylistTextDocument.selectedIds(rows))
    }

    companion object {
        val sample = """
            Map of the Problematique - Muse
            Starlight - Muse
            Invincible - Muse
            Big TV - White Lies
            There Goes Our Love Again - White Lies
            Getting Even - White Lies
            Midnight City - M83
            Steve McQueen - M83
            Outro - M83
            Particles - Nothing But Thieves
            Amsterdam - Nothing But Thieves
            Sorry - Nothing But Thieves
            Late Night - Foals
            My Number - Foals
            Inhaler - Foals
            Bullets - Archive
            Collapse / Collide - Archive
            In Your Room - Depeche Mode
            I Feel You - Depeche Mode
            Rush - Depeche Mode
            Pictures of You - The Cure
            Plainsong - The Cure
            Fascination Street - The Cure
            Low - Coldplay
            Talk - Coldplay
            A Message - Coldplay
        """.trimIndent()
    }
}
