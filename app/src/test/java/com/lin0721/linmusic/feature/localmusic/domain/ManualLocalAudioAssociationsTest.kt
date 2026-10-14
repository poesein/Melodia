package com.lin0721.linmusic.feature.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualLocalAudioAssociationsTest {
    private fun identity(uri: String): String? = when (uri) {
        "media:1", "saf:1" -> "file:1"
        "media:2" -> "file:2"
        else -> null
    }

    @Test
    fun `readable manual file exposes its confirmed online ID`() {
        assertEquals(mapOf("file:1" to 123L),
            ManualLocalAudioAssociations.uniqueByIdentity(mapOf(123L to "saf:1"), ::identity))
    }

    @Test
    fun `multiple online versions bound to one physical file remain ambiguous`() {
        assertTrue(ManualLocalAudioAssociations.uniqueByIdentity(
            mapOf(123L to "media:1", 456L to "saf:1"), ::identity).isEmpty())
    }

    @Test
    fun `unreadable files and nonpositive IDs do not become cloud associations`() {
        assertTrue(ManualLocalAudioAssociations.uniqueByIdentity(
            mapOf(123L to "missing", -5L to "media:1"), ::identity).isEmpty())
    }

    @Test
    fun `relink removes only old aliases of the selected file`() {
        val links = mapOf(123L to "media:1", 456L to "saf:1", 789L to "media:2", 900L to "missing")
        assertEquals(setOf(123L, 456L), ManualLocalAudioAssociations.idsForIdentity(links, "file:1", ::identity))
        assertEquals(mapOf("file:2" to 789L), ManualLocalAudioAssociations.uniqueByIdentity(
            links.filterKeys { it !in setOf(123L, 456L) }, ::identity))
    }
}
