package com.lin0721.linmusic.feature.personallibrary

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import android.net.Uri
import com.lin0721.linmusic.feature.localmusic.domain.*
import com.lin0721.linmusic.core.ui.theme.MelodiaTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PersonalLibraryUiTest {
    @get:Rule val compose = createComposeRule()
    @Composable private fun PageTheme(content: @Composable () -> Unit) {
        MelodiaTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground, content = content) }
    }
    private fun playlists(count: Int) = (1..count).map { LibraryItem("$it", "Playlist $it", "Test collection", "", LibraryItemType.PLAYLIST, trackCount = it * 2) }
    private val shelves = listOf(LibraryItem("artist", "Example Artist", "", "", LibraryItemType.ARTIST),
        LibraryItem("album", "Example Album", "Example Artist", "", LibraryItemType.ALBUM))
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun toolbarInvokesTheFiveExistingActionsAndDoesNotAddAnAvatarOrLibraryHeading() {
        val clicked = mutableListOf<String>()
        compose.setContent { PageTheme { PersonalLibraryToolbar({ clicked += "settings" }, { clicked += "local" },
            { clicked += "search" }, { clicked += "recognition" }, { clicked += "create" }) } }
        listOf("个人音乐库样式设置", "本地音乐", "搜索音乐", "听歌识曲", "新建或导入歌单").forEach {
            compose.onNodeWithContentDescription(it).performClick()
        }
        compose.runOnIdle { assertEquals(listOf("settings", "local", "search", "recognition", "create"), clicked) }
        assertTrue(compose.onNodeWithContentDescription("本地音乐").fetchSemanticsNode().boundsInRoot.left >=
            compose.onNodeWithContentDescription("新建或导入歌单").fetchSemanticsNode().boundsInRoot.right)
        compose.onNodeWithText("音乐库").assertDoesNotExist()
        compose.onNodeWithText("本地音乐详情").assertDoesNotExist()
    }
    @Test fun previewLimitsActualRowsToFiveInOneAndFiveColumnListModesAndOpensTheSelectedPlaylist() {
        var layout by mutableStateOf(LibraryPageLayout())
        var selected: LibraryItem? = null
        compose.setContent { PageTheme { PersonalLibraryContent(playlists(30), layout, { selected = it }, {}) } }
        compose.onNodeWithText("Playlist 5").assertExists()
        compose.onNodeWithText("Playlist 6").assertDoesNotExist()
        compose.onNodeWithText("Playlist 2").performClick()
        compose.runOnIdle { assertEquals("2", selected?.id); layout = layout.copy(listColumns = 5) }
        compose.onNodeWithText("Playlist 25").assertExists()
        compose.onNodeWithText("Playlist 26").assertDoesNotExist()
        screenshot("beta23-personal-list.png")
    }
    @Test fun listShrinksToActualRowsAndMovesArtistAndAlbumSectionsUp() = shrinkingPreview("list")
    @Test fun coverGridShrinksToActualRowsAndMovesArtistAndAlbumSectionsUp() = shrinkingPreview("covers")
    private fun shrinkingPreview(mode: String) {
        var count by mutableIntStateOf(7)
        val layout = LibraryPageLayout(playlistMode = mode, listColumns = 3, coverColumns = 3)
        compose.setContent { PageTheme { PersonalLibraryContent(playlists(count) + shelves, layout, {}, {}) } }
        val before = compose.onNodeWithText("歌手").getUnclippedBoundsInRoot().top.value
        compose.runOnIdle { count = 2 }
        compose.onNodeWithText("歌手").assertIsDisplayed()
        compose.onNodeWithText("专辑").assertIsDisplayed()
        val after = compose.onNodeWithText("歌手").getUnclippedBoundsInRoot().top.value
        assertTrue("A partial preview must release two absent rows: before=$before, after=$after", before - after > 100f)
        compose.onNodeWithText("Playlist 2").assertIsDisplayed()
        compose.onNodeWithText("Playlist 3").assertDoesNotExist()
        if (mode == "covers") screenshot("beta23-personal-covers.png")
    }
    @Test fun settingsReorderAndHideSectionsImmediatelyAndListAndCoverColumnsRemainIndependent() {
        var layout by mutableStateOf(LibraryPageLayout().normalized(LibraryPage.PERSONAL))
        var editing by mutableStateOf(true)
        compose.setContent { PageTheme {
            if (editing) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LibraryPageLayoutEditor(LibraryPage.PERSONAL, LibraryPageLayoutState(layout, loaded = true)) { layout = it(layout) }
                TextButton(onClick = { editing = false }) { Text("完成设置") }
            } else PersonalLibraryContent(playlists(2) + shelves, layout, {}, {})
        } }
        compose.onNodeWithContentDescription("上移歌手").performClick()
        compose.onNodeWithContentDescription("显示专辑").performClick()
        compose.onNodeWithText("5", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("封面").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(5, layout.listColumns); assertEquals(3, layout.coverColumns) }
        compose.onNodeWithText("完成设置").performScrollTo().performClick()
        compose.onNodeWithText("专辑").assertDoesNotExist()
        assertTrue(compose.onNodeWithText("歌手").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithText("歌单").fetchSemanticsNode().boundsInRoot.top)
    }
    @Test fun moreKeepsAllPlaylistsAvailableBeyondTheFiveRowPreview() {
        var selected: LibraryItem? = null
        var dismissed = false
        compose.setContent { PageTheme { PersonalLibraryCollectionSheet(LibraryItemType.PLAYLIST, playlists(30),
            LibraryPageLayout(playlistMode = "covers", coverColumns = 3), { selected = it }, { dismissed = true }) } }
        compose.onNodeWithTag("personal_library_all").performScrollToIndex(29)
        compose.onNodeWithText("Playlist 30").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertEquals("30", selected?.id) }
    }
    @Test fun localLayoutEditorContainsExactlyItsFourExistingSectionsAndNoOnlinePlaylistStyleOptions() {
        var layout by mutableStateOf(LibraryPageLayout().normalized(LibraryPage.LOCAL))
        compose.setContent { PageTheme { LibraryPageLayoutEditor(LibraryPage.LOCAL, LibraryPageLayoutState(layout, loaded = true)) { layout = it(layout) } } }
        listOf("本地歌单", "最近添加", "歌手", "专辑").forEach { compose.onNodeWithContentDescription("显示$it").assertExists() }
        compose.onNodeWithContentDescription("上移最近添加").performClick()
        compose.onNodeWithContentDescription("显示本地歌单").performClick()
        compose.runOnIdle { assertEquals("recent", layout.sections.first().key); assertFalse(layout.sections.first { it.key == "local_playlists" }.visible) }
        compose.onNodeWithText("歌单显示").assertDoesNotExist()
    }
    @Test fun localAndOnlineSectionsCanBeInterleavedAndHiddenWithoutChangingEitherLibrary() {
        val track = LocalTrack(11, null, "Local Song", "Local Artist", "Local Album", 180000, 100,
            Uri.parse("content://media/external/audio/media/11"), null, 123, LocalTrackSource.EXTERNAL)
        val local = PersonalLocalLibrary(buildLocalLibraryIndex(listOf(track)), listOf(LocalPlaylist(1, "Local Playlist", 123, listOf(track))))
        var layout by mutableStateOf(LibraryPageLayout(sections = listOf(
            LibrarySectionLayout("local_recent"), LibrarySectionLayout("playlists"), LibrarySectionLayout("local_artists"),
            LibrarySectionLayout("local_albums"), LibrarySectionLayout("local_playlists"), LibrarySectionLayout("artists", false), LibrarySectionLayout("albums", false))))
        var played: LocalTrack? = null
        compose.setContent { PageTheme { PersonalLibraryContent(playlists(1), layout, {}, {}, local,
            PersonalLocalActions(onTrack = { selected, _ -> played = selected })) } }
        compose.onNodeWithText("Local Song").performClick()
        compose.runOnIdle { assertEquals(track, played) }
        assertTrue(compose.onNodeWithText("本地最近添加").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithText("歌单").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("personal_library_sections").performScrollToNode(hasText("本地歌单"))
        compose.onNodeWithText("Local Playlist").assertExists()
        compose.runOnIdle { layout = layout.copy(sections = layout.sections.map { it.copy(visible = it.key != "local_recent") }) }
        compose.onNodeWithTag("personal_library_sections").performScrollToIndex(0)
        compose.onNodeWithText("本地最近添加").assertDoesNotExist()
        compose.onNodeWithText("Playlist 1").assertExists()
    }
    @Test fun enabledLocalSectionsAreShownWithoutOnlineAccountData() {
        val layout = LibraryPageLayout(sections = listOf(LibrarySectionLayout("local_playlists"),
            LibrarySectionLayout("playlists", false), LibrarySectionLayout("artists", false), LibrarySectionLayout("albums", false)))
        var created = false
        compose.setContent { PageTheme { PersonalLibraryContent(emptyList(), layout, {}, {}, PersonalLocalLibrary(),
            PersonalLocalActions(onCreatePlaylist = { created = true }), onlineStatus = { Text("登录后查看网易云收藏") }) } }
        compose.onNodeWithText("本地歌单").assertIsDisplayed()
        compose.onNodeWithText("新建本地歌单").performClick()
        compose.runOnIdle { assertTrue(created) }
        compose.onNodeWithText("歌单").assertDoesNotExist()
    }
    @Test fun unlimitedArtistShelfReachesBeyondTheOldTenItemLimitAndUsesTheManualOrder() {
        val artists = (1..15).map { LibraryItem("$it", "Artist $it", "", "", LibraryItemType.ARTIST) }
        var selected: String? = null
        val layout = LibraryPageLayout(sections = listOf(LibrarySectionLayout("artists"), LibrarySectionLayout("albums", false), LibrarySectionLayout("playlists", false)),
            itemLimits = mapOf("artists" to 0), itemOrders = mapOf("artists" to listOf("15", "1")))
        compose.setContent { PageTheme { PersonalLibraryContent(artists, layout, { selected = it.id }, {}) } }
        compose.onNodeWithText("Artist 15").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("15", selected) }
        // The original 10-item cap would omit Artist 14 after putting 15 first.
        compose.onNodeWithTag("personal_library_artist_shelf").performScrollToNode(hasText("Artist 14"))
        compose.onNodeWithText("Artist 14").assertIsDisplayed()
    }
}
