package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogAlbumMatch
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogReleaseGroup
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogSelection
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogTitleMatch
import com.lin0721.linmusic.feature.artist.ui.catalog.ArtistCatalogUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ArtistAlbumPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun albumTabKeepsOnlyCompactCountsAboveTheAlbumRows() {
        val album = ArtistAlbum(id = 1, name = "Studio Album", size = 4)
        val selection = ArtistCatalogSelection(
            albums = listOf(album),
            matches = listOf(ArtistCatalogAlbumMatch(album, ArtistCatalogReleaseGroup("g", "Studio Album"), ArtistCatalogTitleMatch.TITLE)),
            unmatchedAlbums = listOf(ArtistAlbum(id = 2)), ambiguousAlbums = listOf(ArtistAlbum(id = 3)),
            excludedAlbums = listOf(ArtistAlbum(id = 4)), duplicateAlbums = listOf(ArtistAlbum(id = 5)),
            unmatchedReleaseGroups = emptyList())
        compose.setContent {
            MaterialTheme {
                LazyColumn {
                    artistAlbumTab(listOf(album), ArtistCatalogUiState(enabled = true, artistName = "Example Artist"),
                        selection, false, false, null, {}, {})
                }
            }
        }
        compose.onNodeWithText("匹配1·未识别2·合并1·排除1").assertIsDisplayed()
        compose.onNodeWithText("Studio Album").assertIsDisplayed()
        compose.onNodeWithText("默认顺序").assertDoesNotExist()
        compose.onNodeWithText("主要录音室专辑 · MusicBrainz").assertDoesNotExist()
        compose.onNodeWithText("资料库歌手：Example Artist").assertDoesNotExist()
        compose.onNodeWithText("已读取完整网易云专辑列表", substring = true).assertDoesNotExist()
        compose.onNodeWithText("资料库设置").assertDoesNotExist()
        compose.onNodeWithText("手动修正").assertDoesNotExist()
        compose.onNodeWithText("恢复全部").assertDoesNotExist()
    }

    @Test
    fun artistMenuKeepsOnlyCatalogSettingsAndClosesBeforeOpeningThem() {
        val calls = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                ArtistMoreMenuSheet(isBlocked = false, catalogEnabled = true,
                    onCatalogSettings = { calls += "settings" },
                    onManualCorrections = { calls += "corrections" },
                    onRestoreAll = { calls += "restore" },
                    onBlockClick = {}, onDismiss = { calls += "dismiss" })
            }
        }
        compose.onNodeWithText("专辑最少曲数").assertDoesNotExist()
        compose.onNodeWithText("只显示录音室专辑").performClick()
        compose.onNodeWithText("手动修正").assertDoesNotExist()
        compose.onNodeWithText("恢复全部").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("dismiss", "settings"), calls)
        }
    }
}
