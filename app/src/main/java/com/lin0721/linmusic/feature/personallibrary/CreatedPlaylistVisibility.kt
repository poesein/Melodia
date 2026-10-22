package com.lin0721.linmusic.feature.personallibrary

import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Process-wide registration covers ordinary creation, add-to-new-playlist and text imports,
 * including creation before the personal-library page has ever been opened. No layout migration.
 */
internal class CreatedPlaylistVisibility(
    bus: PlaylistMutationBus,
    repository: LibraryPageLayoutRepository,
    scope: CoroutineScope,
    onFailure: (Exception) -> Unit
) {
    private val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        bus.events.collect { event ->
            if (event is PlaylistMutationEvent.Created && event.playlistId > 0) {
                try {
                    repository.update(LibraryPage.PERSONAL) { it.withCreatedPlaylistVisible(event.playlistId) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // One failed write must not terminate visibility updates for later creations.
                    onFailure(error)
                }
            }
        }
    }

    fun close() = job.cancel()
}
