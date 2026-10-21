package com.lin0721.linmusic.feature.localmusic.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAudioMetadataTest {
    @Test
    fun `wave is recognized from display name without a filesystem path`() {
        assertTrue(isWaveAudio("01 妈妈一起飞吧.WAV", null))
        assertTrue(isWaveAudio("track.wave", "application/octet-stream"))
        assertFalse(isWaveAudio("track.wav.mp3", null))
    }

    @Test
    fun `wave is recognized from provider mime type without a display name`() {
        listOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave", "AUDIO/X-WAV; codecs=1").forEach {
            assertTrue(isWaveAudio(null, it))
        }
        assertFalse(isWaveAudio(null, "audio/mpeg"))
    }

    @Test
    fun `taglib property mapping retains unicode titles album credits and disc order`() {
        val metadata = localAudioMetadataFromProperties(mapOf(
            "TITLE" to arrayOf(" 妈妈一起飞吧 "), "ARTIST" to arrayOf("左小祖咒", "合作者"),
            "ALBUM" to arrayOf("妈妈一起飞吧，妈妈一起摇滚吧"), "ALBUMARTIST" to arrayOf("左小祖咒"),
            "DATE" to arrayOf("2012-05-04"), "TRACKNUMBER" to arrayOf("03/10"), "DISCNUMBER" to arrayOf("2/2")
        ))
        assertEquals(LocalAudioMetadata("妈妈一起飞吧", "左小祖咒 / 合作者", "妈妈一起飞吧，妈妈一起摇滚吧",
            "左小祖咒", 2012, 2003), metadata)
    }

    @Test
    fun `wave embedded tags override a filename masquerading as system title`() {
        val system = LocalAudioMetadata("01 左小祖咒 - 妈妈一起飞吧", "<unknown>", "Music")
        val embedded = LocalAudioMetadata("妈妈一起飞吧", "左小祖咒", "妈妈一起飞吧，妈妈一起摇滚吧")
        assertEquals(embedded, mergeLocalAudioMetadata(system, embedded, preferEmbedded = true))
    }

    @Test
    fun `other formats preserve every valid system tag while filling absent fields`() {
        val system = LocalAudioMetadata("System title", "System artist", "System album", year = 2001, trackNumber = 4)
        val embedded = LocalAudioMetadata("Embedded title", "Embedded artist", "Embedded album", "Album credit", 2002, 8)
        assertEquals(system.copy(albumArtist = "Album credit"),
            mergeLocalAudioMetadata(system, embedded, preferEmbedded = false))
    }

    @Test
    fun `blank and unknown embedded values cannot erase valid system tags`() {
        val system = LocalAudioMetadata("Title", "Artist", "Album", "Credit", 2001, 1)
        val embedded = LocalAudioMetadata(" ", "<unknown>", "<UNKNOWN>", "", 0, -1)
        assertEquals(system, mergeLocalAudioMetadata(system, embedded, preferEmbedded = true))
    }

    @Test
    fun `failed native read preserves the existing decoder metadata`() {
        val system = LocalAudioMetadata("Title", "Artist", "Album", "Credit", 2001, 1)
        assertEquals(system, mergeLocalAudioMetadata(system, null, preferEmbedded = true))
        assertEquals(system, mergeLocalAudioMetadata(system, null, preferEmbedded = false))
    }

    @Test
    fun `complete nonwave metadata avoids a second tag read but wave still requests one`() {
        val complete = LocalAudioMetadata("Title", "Artist", "Album", "Credit", 2001, 1)
        assertFalse(needsEmbeddedAudioTags(complete, wave = false))
        assertTrue(needsEmbeddedAudioTags(complete, wave = true))
        assertTrue(needsEmbeddedAudioTags(complete.copy(album = "<unknown>"), wave = false))
    }

    @Test
    fun `association inspection does not read tags solely for fields it never uses`() {
        val complete = LocalAudioMetadata("Title", "Artist", "Album")
        assertFalse(needsEmbeddedAudioTags(complete, wave = false, extendedFields = false))
        assertTrue(needsEmbeddedAudioTags(complete, wave = false))
    }

    @Test
    fun `empty properties leave absent tags absent and invalid numbering cannot overflow`() {
        assertEquals(LocalAudioMetadata(), localAudioMetadataFromProperties(emptyMap()))
        assertNull(localTrackNumberFromTags("0", "1"))
        assertNull(localTrackNumberFromTags("1", Int.MAX_VALUE.toString()))
        assertEquals(3, localTrackNumberFromTags("3/50", null))
    }

    @Test
    fun `valid song and album titles named Unknown are preserved`() {
        val metadata = localAudioMetadataFromProperties(mapOf("TITLE" to arrayOf("Unknown"), "ALBUM" to arrayOf("Unknown Album")))
        assertEquals("Unknown", metadata.title)
        assertEquals("Unknown Album", metadata.album)
    }
}
