package com.lin0721.linmusic.feature.artist.ui.catalog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistAlbumCorrection
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistAlbumCorrections
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArtistAlbumCorrectionsUiTest {
    @get:Rule val compose = createComposeRule()
    private val automatic = ArtistAlbum(1, "Automatic Album", size = 10)
    private val missed = ArtistAlbum(2, "Important EP", size = 4, alias = listOf("Missing classic"))
    private val albums = listOf(automatic, missed)
    private var value by mutableStateOf(ArtistAlbumCorrections())
    private fun show(catalogEnabled: Boolean = true) {
        compose.setContent {
            MaterialTheme {
                ArtistAlbumCorrectionsContent("Example Artist", albums, listOf(automatic),
                    ArtistAlbumCorrectionsState(artistId = 1, value = value, loaded = true), catalogEnabled,
                    false, false, null, {}, {}, { value = value.copy(enabled = it) },
                    { album, show -> value = value.copy(entries = value.entries.filterNot { it.album.id == album.id } + ArtistAlbumCorrection(album, show)) },
                    { id, active -> value = value.copy(entries = value.entries.map { if (it.album.id == id) it.copy(active = active) else it }) },
                    {}, Modifier.fillMaxSize())
            }
        }
    }
    private fun clickDescription(description: String) {
        compose.onNodeWithTag("album_corrections_list").performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).performClick()
    }
    private fun clickText(text: String) {
        compose.onNodeWithTag("album_corrections_list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    @Test fun addingAndHidingAreOneTapAndDoNotRequireAConfirmationDialog() {
        show()
        clickDescription("添加专辑：Important EP")
        compose.runOnIdle { assertEquals(listOf(automatic, missed), value.apply(listOf(automatic), albums, true)) }
        clickDescription("隐藏专辑：Automatic Album")
        compose.runOnIdle { assertEquals(listOf(missed), value.apply(listOf(automatic), albums, true)) }
        compose.onNodeWithText("手动修正专辑").assertExists()
        compose.onNodeWithTag("album_corrections_list").performScrollToIndex(0)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.cacheDir, "beta22-manual-albums.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun restoringAutomaticAndReapplyingKeepsBothAddedAndHiddenChoices() {
        value = ArtistAlbumCorrections(entries = listOf(ArtistAlbumCorrection(missed, true), ArtistAlbumCorrection(automatic, false)))
        show()
        clickText("恢复自动筛选（保留记录）")
        compose.runOnIdle {
            assertFalse(value.enabled)
            assertEquals(2, value.entries.size)
            assertEquals(listOf(automatic), value.apply(listOf(automatic), albums, true))
        }
        clickText("重新应用已保存修正")
        compose.runOnIdle { assertEquals(listOf(missed), value.apply(listOf(automatic), albums, true)) }
    }

    @Test fun followingAutomaticForOneAlbumRetainsItsSavedDecision() {
        value = ArtistAlbumCorrections(entries = listOf(ArtistAlbumCorrection(missed, true)))
        show()
        clickText("此项跟随自动（保留记录）")
        compose.runOnIdle {
            assertTrue(value.entries.single().show)
            assertFalse(value.entries.single().active)
            assertEquals(listOf(automatic), value.apply(listOf(automatic), albums, true))
        }
        clickText("恢复此项修正")
        compose.runOnIdle { assertEquals(listOf(automatic, missed), value.apply(listOf(automatic), albums, true)) }
    }

    @Test fun hiddenFilterAndAliasSearchFindAnAlbumExcludedByTheAutomaticResult() {
        show()
        clickText("已隐藏")
        compose.onNode(hasSetTextAction()).performTextReplacement("Missing classic")
        compose.onNodeWithTag("album_corrections_list").performScrollToNode(hasText("Important EP"))
        compose.onNodeWithText("Important EP").assertIsDisplayed()
        compose.onNodeWithText("Automatic Album").assertDoesNotExist()
    }

    @Test fun turningOffCatalogueKeepsRecordsAndDisablesEditingTheUnfilteredList() {
        value = ArtistAlbumCorrections(entries = listOf(ArtistAlbumCorrection(missed, true)))
        show(catalogEnabled = false)
        compose.onNodeWithTag("album_corrections_list").performScrollToNode(hasContentDescription("添加专辑：Important EP"))
        compose.onNodeWithContentDescription("添加专辑：Important EP").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, value.entries.size) }
    }
}
