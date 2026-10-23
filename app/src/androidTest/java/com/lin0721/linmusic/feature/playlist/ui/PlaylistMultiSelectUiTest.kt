package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lin0721.linmusic.core.ui.components.*
import com.lin0721.linmusic.core.ui.theme.MelodiaTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaylistMultiSelectUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun likesAndOrdinaryPlaylistsShareEditSongsAndKeepOrderingSeparate() {
        var likes by mutableStateOf(true)
        var edits = 0; var reorders = 0
        compose.setContent { MelodiaTheme { Surface {
            PlaylistActionPillsRow(!likes, false, PlaylistTrackSort(), {}, { reorders++ }, {}, {},
                isLikedSongsPlaylist = likes, onEditSongsClick = { edits++ })
        } } }
        compose.onNodeWithText("编辑歌曲").performClick()
        compose.onNodeWithText("调整顺序").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, edits); likes = false }
        compose.onNodeWithText("编辑歌曲").performClick()
        compose.onNodeWithText("调整顺序").performClick()
        compose.runOnIdle { assertEquals(2, edits); assertEquals(1, reorders) }
    }

    @Test fun multiSelectOffersBothHeartActionsAndMovingOutOnlyForOwnedPlaylists() {
        var owned by mutableStateOf(true)
        var busy by mutableStateOf(false)
        val calls = mutableListOf<String>()
        compose.setContent { MelodiaTheme { Surface(Modifier.fillMaxSize()) { Box {
            TrackSelectionOverlay(TrackSelectionUi(active = true, selectedIds = setOf(1, 2), busy = busy,
                progressText = if (busy) "正在取消喜欢 1/2" else null),
                playlistSelectionActions(owned, { calls += "next" }, { calls += "add" }, { calls += "like" },
                    { calls += "unlike" }, { calls += "download" }, { calls += "remove" }),
                listOf(1, 2), androidx.compose.material3.MaterialTheme.colorScheme.background, {}, { calls += "stop" })
        } } } }
        compose.onNodeWithText("加入喜欢").performClick()
        compose.onNodeWithText("取消喜欢").performClick()
        compose.onNodeWithText("添加到歌单").performClick()
        compose.onNodeWithText("移出歌单").performClick()
        compose.runOnIdle { assertEquals(listOf("like", "unlike", "add", "remove"), calls) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "beta30-multi-select.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("正在取消喜欢 1/2").assertExists()
        compose.onNodeWithText("取消喜欢").assertIsNotEnabled()
        compose.onNodeWithContentDescription("停止批量操作").performClick()
        compose.runOnIdle { assertEquals("stop", calls.last()); busy = false; owned = false }
        compose.onNodeWithText("移出歌单").assertDoesNotExist()
        compose.onNodeWithText("取消喜欢").assertExists()
    }
}
