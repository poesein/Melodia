package com.lin0721.linmusic.feature.artist.ui.catalog

import androidx.lifecycle.ViewModelStore
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogHttpResponse
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogPreferenceStorage
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogRepository
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogRequestGate
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogTransport
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistCatalogViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val artist = ArtistDetailInfo(id = 123L, name = "Example Artist")
    private val albums = listOf(ArtistAlbum(id = 1L, name = "First Album"), ArtistAlbum(id = 2L, name = "Second Album"))

    @Test fun disabledSettingDoesNotRequestCatalogueWhenAlbumsOrArtistContextLoads() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport()
        val preferences = FakePreferences()
        val repository = repository(preferences, transport, dispatcher)
        val model = ArtistCatalogViewModel(repository)
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums.take(1))
            advanceUntilIdle()
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            assertFalse(model.state.value.enabled)
            assertEquals(0, transport.requests)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun uniqueExactNameNeedsTwoDistinctAlbumMatchesBeforeAutomaticBinding() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport()
        val preferences = FakePreferences(ArtistCatalogPreference(enabled = true))
        val model = ArtistCatalogViewModel(repository(preferences, transport, dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            assertEquals(MBID, model.state.value.artistMbid)
            assertEquals("Example Artist", model.state.value.artistName)
            assertEquals(2, model.state.value.groups?.size)
            assertFalse(model.state.value.choosingArtist)
            assertNull(model.state.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun oneAlbumLeavesCandidatesForExplicitChoiceAndLaterPageCanConfirmIdentity() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport()
        val preferences = FakePreferences(ArtistCatalogPreference(enabled = true))
        val model = ArtistCatalogViewModel(repository(preferences, transport, dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums.take(1))
            advanceUntilIdle()
            assertNull(model.state.value.artistMbid)
            assertNull(model.state.value.groups)
            assertEquals(1, model.state.value.candidates.size)
            assertTrue(model.state.value.choosingArtist)
            val requested = transport.requests
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            assertEquals(MBID, model.state.value.artistMbid)
            assertEquals(requested, transport.requests)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedRefreshKeepsUsableCatalogueAndTurningOffMakesNoFurtherRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport()
        val preferences = FakePreferences(ArtistCatalogPreference(true, MBID, "Example Artist"))
        val repository = repository(preferences, transport, dispatcher)
        repository.albums(MBID).getOrThrow()
        val model = ArtistCatalogViewModel(repository)
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            transport.fail = true
            model.retry()
            advanceUntilIdle()
            assertEquals(2, model.state.value.groups?.size)
            assertNotNull(model.state.value.error)
            assertFalse(model.state.value.loading)
            val requested = transport.requests
            model.setEnabled(false)
            advanceUntilIdle()
            model.updateArtistContext(artist, albums + ArtistAlbum(id = 3L, name = "Unknown Album"))
            advanceUntilIdle()
            assertFalse(model.state.value.enabled)
            assertEquals(requested, transport.requests)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun failureWithoutCacheLeavesCatalogueUnavailableInsteadOfAnEmptySuccessfulList() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport().apply { fail = true }
        val preferences = FakePreferences(ArtistCatalogPreference(true, MBID, "Example Artist"))
        val model = ArtistCatalogViewModel(repository(preferences, transport, dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            assertNull(model.state.value.groups)
            assertNotNull(model.state.value.error)
            assertTrue(model.state.value.enabled)
            assertFalse(model.state.value.loading)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    private fun repository(preferences: FakePreferences, transport: FakeTransport,
        dispatcher: CoroutineDispatcher) = ArtistCatalogRepository(
        cacheDirectory = temporary.newFolder(), preferences = preferences, transport = transport,
        gate = object : ArtistCatalogRequestGate {
            override suspend fun <T> request(action: suspend () -> T): T = action()
        }, ioDispatcher = dispatcher)

    private class FakePreferences(initial: ArtistCatalogPreference = ArtistCatalogPreference()) : ArtistCatalogPreferenceStorage {
        private val values = mutableMapOf(123L to MutableStateFlow(initial))
        private fun value(id: Long) = values.getOrPut(id) { MutableStateFlow(ArtistCatalogPreference()) }
        override fun observe(artistId: Long) = value(artistId)
        override suspend fun setEnabled(artistId: Long, enabled: Boolean) { value(artistId).value = value(artistId).value.copy(enabled = enabled) }
        override suspend fun bind(artistId: Long, mbid: String, name: String?) {
            value(artistId).value = value(artistId).value.copy(artistMbid = mbid, artistName = name)
        }
        override suspend fun clearBinding(artistId: Long) { value(artistId).value = value(artistId).value.copy(artistMbid = null, artistName = null) }
    }

    private class FakeTransport : ArtistCatalogTransport {
        var requests = 0
        var fail = false
        override suspend fun get(url: okhttp3.HttpUrl): ArtistCatalogHttpResponse {
            requests++
            if (fail) return ArtistCatalogHttpResponse(503, "{}")
            if (url.encodedPath.endsWith("/artist")) return ArtistCatalogHttpResponse(200,
                """{"count":1,"offset":0,"artists":[{"id":"$MBID","name":"Example Artist","aliases":[]}]}""")
            return ArtistCatalogHttpResponse(200,
                """{"release-group-count":2,"release-group-offset":0,"release-groups":[
                    {"id":"21111111-1111-4111-8111-111111111111","title":"First Album","primary-type":"Album","secondary-types":[],"artist-credit":[{"artist":{"id":"$MBID","name":"Example Artist"}}]},
                    {"id":"31111111-1111-4111-8111-111111111111","title":"Second Album","primary-type":"Album","secondary-types":[],"artist-credit":[{"artist":{"id":"$MBID","name":"Example Artist"}}]}
                ]}""")
        }
    }

    companion object { private const val MBID = "11111111-1111-4111-8111-111111111111" }
}
