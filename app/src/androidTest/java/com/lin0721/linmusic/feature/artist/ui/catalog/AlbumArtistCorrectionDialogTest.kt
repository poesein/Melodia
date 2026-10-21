package com.lin0721.linmusic.feature.artist.ui.catalog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.lin0721.linmusic.core.model.Artist
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AlbumArtistCorrectionDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun collaborationRequiresChoosingOneAlbumArtistBeforeAdding() {
        val artists = listOf(Artist(1, "First Artist"), Artist(2, "Second Artist"))
        var chosen: Artist? = null
        compose.setContent {
            var selected by remember { mutableStateOf<Artist?>(null) }
            MaterialTheme {
                AlbumArtistCorrectionDialog("Album", artists, selected, true, false, false, null,
                    { selected = it }, {}, { chosen = selected })
            }
        }
        compose.onNodeWithText("添加").assertIsNotEnabled()
        compose.onNodeWithText("Second Artist").performClick()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.cacheDir, "beta22-album-artist-picker.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("添加").performClick()
        compose.runOnIdle { assertEquals(2L, chosen?.id) }
    }

    @Test fun missingCreditsCannotCreateAnAssociation() {
        compose.setContent { MaterialTheme {
            AlbumArtistCorrectionDialog("Album", emptyList(), null, false, false, false, null, {}, {}, {})
        } }
        compose.onNodeWithText("添加").assertIsNotEnabled()
        compose.onNodeWithText("专辑缺少有效的署名歌手，请重新加载专辑详情后再试。").assertIsDisplayed()
    }

    @Test fun pausedChoicesAreExplicitlyDisclosedBeforeShortcutReenablesThem() {
        var saved = false
        val artist = Artist(1, "Artist")
        compose.setContent { MaterialTheme {
            AlbumArtistCorrectionDialog("Album", listOf(artist), artist, true, true, false, null, {}, {}, { saved = true })
        } }
        compose.onNodeWithText("此歌手的手动修正已暂停。添加后将重新应用此前保存的添加和隐藏记录。").assertIsDisplayed()
        compose.onNodeWithText("添加并启用手动修正").performClick()
        compose.runOnIdle { assertTrue(saved) }
    }
}
