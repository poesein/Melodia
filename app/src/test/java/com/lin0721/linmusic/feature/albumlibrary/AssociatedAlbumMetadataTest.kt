package com.lin0721.linmusic.feature.albumlibrary

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.localmusic.data.LocalAlbumAssociation
import org.junit.Assert.*
import org.junit.Test

class AssociatedAlbumMetadataTest {
    @Test fun savedPairingProvidesOnlineIdsInOrderWithoutInventingUnknownTracks() {
        val saved = LocalAlbumAssociation("folder", Album(9, "Record"), listOf(
            Track(id = 12, al = Album(9)), Track(id = 11, al = Album(9))))
        assertEquals(listOf(12L, 11L), associatedAlbumMetadata(9, listOf(saved))!!.tracks.map { it.id })
        assertNull(associatedAlbumMetadata(8, listOf(saved)))
        assertNull(associatedAlbumMetadata(9, listOf(saved.copy(tracks = emptyList()))))
        assertNull(associatedAlbumMetadata(9, listOf(saved.copy(tracks = listOf(Track(0))))))
        assertNull(associatedAlbumMetadata(9, listOf(saved.copy(tracks = listOf(Track(12, al = Album(8)))))))
    }
}
