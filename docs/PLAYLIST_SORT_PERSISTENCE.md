# Playlist display order persistence

The detail page previously retained its sorting key and reverse flag only in Compose saved state. Popping that navigation frame, or restarting the app without that saved instance, restored the default order.

## Behavior

- Sorting is a local display/playback preference. It does not reorder the server playlist or mutate an already playing queue.
- The independent `playlist_track_sort` DataStore namespaces entries by account ID (or guest), resource kind (playlist or album), and resource ID. A playlist's saved reverse flag cannot reverse an album with the same ID. Albums without an explicit choice retain their original sequence.
- Both the selected sorting key and direction are saved. Reversal continues to apply to the latest fetched playlist contents, including newly added songs.
- The page waits for the actual account and its persisted sort before building the song list. This prevents rendering a default-order prefix or starting pagination before a saved reverse choice is known. Existing complete-list and latest-playback-sort guards remain in the shared loader/UI.
- Every retained navigation frame continuously observes the repository's accepted state for its scope. A change in another frame updates the older frame without requiring a new target ID.
- Accepted writes run in a repository-owned, serialized application scope, independent of the page ViewModel. Immediately popping a frame cannot cancel the queued preference transaction. Rapid changes coalesce by revision; a finishing older transaction cannot restore an older choice.
- The repository is an application singleton and implements `Closeable`; its DI `onClose` cancels the writer scope and clears its in-memory accepted entries. It has no global static task or page lifecycle owner.
- Unrecognized future/invalid saved formats are retained. An explicit reset affects only the current account/resource. Save failures are visible and can be retried. Process termination before a storage transaction completes is not a durability guarantee.

## Integration boundary

The feature lives in `feature/playlist/ui/PlaylistTrackSortRepository.kt` and `PlaylistTrackSortViewModel.kt`, with a small binding in `PlaylistScreen.kt` and singleton/ViewModel DI registrations. It does not add dependencies to the shared `PlaylistViewModel`, server API, playback controller or account preferences. A future upstream merge should retain the saved-order gate and pass the restored `PlaylistTrackSort` to both rows and queue construction.

## Validation

`PlaylistTrackSortRepositoryTest` checks real preference-file recreation, account/guest/resource isolation, every key/direction pair, unrelated preferences and explicit recovery of future/invalid values.

`PlaylistTrackSortViewModelTest` checks delayed loading, rapid changes, account switching, stale callbacks/reads, failures/retry, two retained frames sharing the same scope, and immediate cancellation of a frame before its accepted write starts. The cancellation regression reads a new repository after the transaction to avoid passing from an optimistic cache.

`PlaylistTrackSortPersistenceTest` recreates the actual Android DataStore and verifies restored key/direction and resource isolation. Existing reverse-list UI and new-song queue tests remain applicable. Test execution and APK acceptance results belong in the release report rather than being inferred from these source tests.

# Settings organization

The settings host routes existing feature-owned preferences into three groups: library/pages, playback/sources, and system/application. `DisplaySettingsView` contains page/navigation/player layout; `LocalAudioSettingsView` contains local playback policy, matching and association backups; `ArtistAlbumsSettingsView` contains the catalog default. `ExtensionsSettingsView` retains device integrations. Original preference keys and feature implementations are reused; local scanner settings use their existing screen instead of a nested copy. Personal library and collected-album settings return to their page-layout parent; local scanner settings return to local audio/matching. Minimum album track count is removed independently of catalog filtering and manual corrections.
