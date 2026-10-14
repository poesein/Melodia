package com.lin0721.linmusic

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.Json
import org.junit.Test

class MelodiaNavigationStateTest {

    // 状态读写发生在组合之外，需显式包一层快照
    private fun <T> inSnapshot(block: () -> T): T {
        val snapshot = Snapshot.takeMutableSnapshot()
        return try {
            val result = snapshot.enter(block)
            snapshot.apply()
            result
        } finally {
            snapshot.dispose()
        }
    }

    @Test
    fun `初始处于主页且无法回退`() = inSnapshot {
        val nav = MelodiaNavigationState()
        assertEquals(Screen.Home, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `跳转到详情页后可以回退`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateTo(Screen.Settings)
        assertEquals(Screen.Settings, nav.currentScreen)
        assertTrue(nav.canNavigateBack)

        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `重复跳转到当前页不入栈`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateTo(Screen.Settings)
        nav.navigateTo(Screen.Settings)
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
    }

    @Test
    fun `跳转到主页会清空历史`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateTo(Screen.Settings)
        nav.navigateTo(Screen.Artist(1L))
        nav.navigateTo(Screen.Home)
        assertEquals(Screen.Home, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `底栏入口始终保留主页作为回退目标`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateTo(Screen.Settings)
        nav.navigateTo(Screen.Artist(1L))
        nav.navigateTo(Screen.Library)

        assertEquals(Screen.Library, nav.currentScreen)
        nav.navigateBack()
        // 中间的 Settings/Artist 已被清掉，直接回到主页
        assertEquals(Screen.Home, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `已在栈底时回退不越界`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateBack()
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
    }

    @Test
    fun `打开歌单会记录ID与专辑标记并跳转`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openPlaylist(id = 123L, isAlbum = true)
        assertEquals(Screen.Playlist(123L, true), nav.currentScreen)
    }

    @Test
    fun `打开歌手会记录ID并跳转`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openArtist(456L)
        assertEquals(Screen.Artist(456L), nav.currentScreen)
    }

    @Test
    fun `从主页搜索框进入时自动聚焦`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openSearch(autoFocus = true)
        assertEquals(Screen.Search, nav.currentScreen)
        assertTrue(nav.searchAutoFocus)
    }

    @Test
    fun `从底栏进入搜索页时不自动聚焦`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openSearch(autoFocus = true)
        nav.navigateTo(Screen.Home)
        nav.openTab(Screen.Search)
        assertEquals(Screen.Search, nav.currentScreen)
        assertFalse(nav.searchAutoFocus)
    }

    @Test
    fun `非相邻的同类型页面各自保留自己的参数`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openPlaylist(id = 1L, isAlbum = false)
        nav.openArtist(2L)
        nav.openPlaylist(id = 3L, isAlbum = false)
        assertEquals(Screen.Playlist(3L, false), nav.currentScreen)

        nav.navigateBack()
        assertEquals(Screen.Artist(2L), nav.currentScreen)

        nav.navigateBack()
        assertEquals(Screen.Playlist(1L, false), nav.currentScreen)
    }

    @Test
    fun `从播放器跳转后退回起点会提示重新打开播放器`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openArtist(1L)
        assertFalse(nav.isNavigatingFromPlayer)

        nav.navigateFromPlayer { nav.openArtist(2L) }
        assertTrue(nav.isNavigatingFromPlayer)
        assertEquals(Screen.Artist(2L), nav.currentScreen)

        val shouldReopenPlayer = nav.navigateBack()
        assertTrue(shouldReopenPlayer)
        assertFalse(nav.isNavigatingFromPlayer)
        assertEquals(Screen.Artist(1L), nav.currentScreen)
    }

    @Test
    fun `关闭播放器会重置播放器跳转标记`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateFromPlayer { nav.openArtist(1L) }
        assertTrue(nav.isNavigatingFromPlayer)

        nav.resetPlayerNavigation()
        assertFalse(nav.isNavigatingFromPlayer)
    }

    @Test
    fun `导航快照包含本地音乐页面时可以序列化并还原`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateTo(Screen.Library)
        nav.openLocalMusic()
        nav.navigateTo(Screen.LocalMusicSettings)
        nav.navigateTo(Screen.LocalArtist("茶太"))
        nav.navigateTo(Screen.LocalAlbum("id:42"))
        nav.navigateTo(Screen.LocalFolder("/sdcard/Music"))
        nav.navigateTo(Screen.LocalPlaylists)
        nav.navigateTo(Screen.LocalPlaylist(7L))

        val json = Json.encodeToString(NavigationSnapshot.serializer(), nav.toSnapshot())
        val restored = Json.decodeFromString(NavigationSnapshot.serializer(), json)
        assertEquals(listOf(Screen.Library), restored.libraryStack)
        assertEquals(
            listOf(
                Screen.LocalMusic,
                Screen.LocalMusicSettings,
                Screen.LocalArtist("茶太"),
                Screen.LocalAlbum("id:42"),
                Screen.LocalFolder("/sdcard/Music"),
                Screen.LocalPlaylists,
                Screen.LocalPlaylist(7L)
            ),
            restored.localMusicStack
        )
    }

    @Test
    fun `旧主页音乐入口转入独立音乐根页面`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        assertEquals(Screen.Music, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertTrue(nav.canGoBackToHomeAll)

        nav.navigateBack()
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
    }

    @Test
    fun `独立音乐页返回主页后保留最新选择`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        nav.updateShowMusicNewWorks(true)
        assertTrue(nav.showMusicNewWorks)
        assertTrue(nav.canGoBackToHomeAll)

        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertTrue(nav.showMusicNewWorks)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
        nav.openTab(Screen.Music)
        assertTrue(nav.showMusicNewWorks)
    }

    @Test
    fun `从其他Tab根页面回退到首页时重置分类为全部`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        nav.navigateTo(Screen.Library)
        assertEquals(Screen.Library, nav.currentScreen)

        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
    }

    @Test
    fun `从音乐详情回退到独立音乐根页面`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        nav.openPlaylist(1L, false)
        assertEquals(Screen.Playlist(1L, false), nav.currentScreen)

        nav.navigateBack()
        assertEquals(Screen.Music, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertTrue(nav.canGoBackToHomeAll)

        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
    }

    @Test
    fun `从首页播客二级页面回退到栈底时保留播客分类`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_PODCAST)
        nav.navigateTo(Screen.PodcastCategory(100L, "分类"))
        assertEquals(Screen.PodcastCategory(100L, "分类"), nav.currentScreen)

        // 第一次返回回到播客分类
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_PODCAST, nav.homeTab)
        assertTrue(nav.canGoBackToHomeAll)

        // 第二次返回切回全部
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
    }

    @Test
    fun `从最新详情返回音乐保留最新并可再切换默认内容`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        nav.updateShowMusicNewWorks(true)
        nav.openPlaylist(1L, true)
        assertEquals(Screen.Playlist(1L, true), nav.currentScreen)

        // 第一次返回保留最新Feed展开态
        nav.navigateBack()
        assertEquals(Screen.Music, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertTrue(nav.showMusicNewWorks)
        assertTrue(nav.canGoBackToHomeAll)

        nav.updateShowMusicNewWorks(false)
        assertFalse(nav.showMusicNewWorks)
        assertEquals(Screen.Music, nav.currentScreen)
        assertTrue(nav.canGoBackToHomeAll)

        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
    }

    @Test
    fun `栈里重复出现的同一目标拥有不同的栈帧 id`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openArtist(1L)
        val first = nav.currentEntry
        nav.openPlaylist(2L, false)
        nav.openArtist(1L)
        val second = nav.currentEntry

        assertEquals(first.screen, second.screen)
        assertTrue(first.id != second.id)
    }

    @Test
    fun `出栈后栈帧 id 不再存活，其余仍存活`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openArtist(1L)
        val artistId = nav.currentEntry.id
        nav.openPlaylist(2L, true)
        val playlistId = nav.currentEntry.id

        nav.navigateBack()

        assertTrue(artistId in nav.liveEntryIds)
        assertFalse(playlistId in nav.liveEntryIds)
    }

    @Test
    fun `各 tab 的栈帧 id 互不重复`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.Library)
        nav.openArtist(1L)
        nav.openTab(Screen.Home)

        val ids = nav.liveEntryIds
        assertEquals(9, ids.size)
    }

    @Test
    fun `新增顶层 tab 拥有独立回退栈且点击底栏 tab 时重置为第一级界面`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.LocalMusic)
        assertEquals(Screen.LocalMusic, nav.currentScreen)

        nav.navigateTo(Screen.LocalSongs)
        assertEquals(Screen.LocalSongs, nav.currentScreen)
        assertTrue(nav.canNavigateBack)

        nav.openTab(Screen.Home)
        assertEquals(Screen.Home, nav.currentScreen)

        // 3A 规则：点击底栏 Tab 时重置目标 Tab 栈至第一级界面
        nav.openTab(Screen.LocalMusic)
        assertEquals(Screen.LocalMusic, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `动态更新首选根 Tab 后回退时回退到新根 Tab`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.updateDefaultRootTab(Screen.Search)
        nav.openTab(Screen.Library)
        assertEquals(Screen.Library, nav.currentScreen)

        nav.navigateBack()
        assertEquals(Screen.Search, nav.currentScreen)
    }

    @Test
    fun `快照恢复后保留栈帧 id 且后续分配不重复`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openArtist(1L)
        nav.openPlaylist(2L, false)
        val before = nav.liveEntryIds
        val currentId = nav.currentEntry.id

        val snapshot = nav.toSnapshot()
        val restored = MelodiaNavigationState(
            initialHomeStack = snapshot.homeStack,
            initialSearchStack = snapshot.searchStack,
            initialLibraryStack = snapshot.libraryStack,
            initialActiveTab = Screen.Home,
            initialHomeIds = snapshot.homeIds,
            initialSearchIds = snapshot.searchIds,
            initialLibraryIds = snapshot.libraryIds,
            initialNextEntryId = snapshot.nextEntryId,
            initialLocalMusicStack = snapshot.localMusicStack,
            initialDownloadsStack = snapshot.downloadsStack,
            initialCloudStack = snapshot.cloudStack,
            initialMyProfileStack = snapshot.myProfileStack,
            initialLocalMusicIds = snapshot.localMusicIds,
            initialDownloadsIds = snapshot.downloadsIds,
            initialCloudIds = snapshot.cloudIds,
            initialMyProfileIds = snapshot.myProfileIds,
            initialMusicStack = snapshot.musicStack,
            initialMusicIds = snapshot.musicIds,
            initialShowMusicNewWorks = snapshot.showMusicNewWorks
        )

        assertEquals(before, restored.liveEntryIds)
        assertEquals(currentId, restored.currentEntry.id)
        restored.openArtist(9L)
        assertFalse(restored.currentEntry.id in before)
    }

    @Test
    fun `旧版本快照缺少栈帧 id 时重新分配`() = inSnapshot {
        val legacy = Json { ignoreUnknownKeys = true }.decodeFromString(
            NavigationSnapshot.serializer(),
            """{"homeStack":[{"type":"com.lin0721.linmusic.Screen.Home"}],"searchStack":[{"type":"com.lin0721.linmusic.Screen.Search"}],"libraryStack":[{"type":"com.lin0721.linmusic.Screen.Library"}],"activeTabIndex":0,"homeTab":0}"""
        )
        val restored = MelodiaNavigationState(
            initialHomeStack = legacy.homeStack,
            initialSearchStack = legacy.searchStack,
            initialLibraryStack = legacy.libraryStack,
            initialHomeIds = legacy.homeIds,
            initialSearchIds = legacy.searchIds,
            initialLibraryIds = legacy.libraryIds,
            initialNextEntryId = legacy.nextEntryId
        )

        assertEquals(8, restored.liveEntryIds.size)
    }

    @Test
    fun `点击底栏Tab按钮时始终重置目标Tab回退栈至第一级界面`() = inSnapshot {
        val nav = MelodiaNavigationState()
        // 主页打开设置页
        nav.navigateTo(Screen.Settings)
        assertEquals(Screen.Settings, nav.currentScreen)

        // 点击底栏切换到搜索页
        nav.openTab(Screen.Search)
        assertEquals(Screen.Search, nav.currentScreen)

        // 点击底栏切回主页，应回到主页第一级界面而不是设置页
        nav.openTab(Screen.Home)
        assertEquals(Screen.Home, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `音乐导航栈与最新选中态经过序列化恢复且详情返回正常`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.Music)
        nav.updateShowMusicNewWorks(true)
        nav.openPlaylist(88L, true)
        val originalIds = nav.liveEntryIds
        val encoded = Json.encodeToString(NavigationSnapshot.serializer(), nav.toSnapshot())
        val restored = restoreNavigationSnapshot(Json.decodeFromString(NavigationSnapshot.serializer(), encoded))
        assertEquals(Screen.Playlist(88L, true), restored.currentScreen)
        assertTrue(restored.showMusicNewWorks)
        assertEquals(originalIds, restored.liveEntryIds)
        restored.navigateBack()
        assertEquals(Screen.Music, restored.currentScreen)
        assertTrue(restored.showMusicNewWorks)
        restored.openArtist(99L)
        assertFalse(restored.currentEntry.id in originalIds)
    }

    @Test
    fun `偏好搜索入口重置旧搜索详情并自动聚焦真实搜索页`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.Search)
        nav.openArtist(42L)
        nav.openTab(Screen.Music)
        nav.openSearch(autoFocus = true)
        assertEquals(Screen.Search, nav.currentScreen)
        assertTrue(nav.searchAutoFocus)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `旧首页音乐快照的详情迁移到独立音乐栈且保留帧ID`() = inSnapshot {
        val old = NavigationSnapshot(
            homeStack = listOf(Screen.Home, Screen.Style(3L, "曲风")),
            homeTab = com.lin0721.linmusic.feature.home.ui.TAB_MUSIC,
            homeIds = listOf(10L, 11L),
            activeTabScreen = Screen.Home,
            nextEntryId = 12L
        )
        val restored = restoreNavigationSnapshot(old)
        assertEquals(Screen.Music, restored.activeTab)
        assertEquals(Screen.Style(3L, "曲风"), restored.currentScreen)
        assertEquals(11L, restored.currentEntry.id)
        restored.navigateBack()
        assertEquals(Screen.Music, restored.currentScreen)
        assertEquals(10L, restored.currentEntry.id)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, restored.homeTab)
    }

    @Test
    fun `旧tab下标不受音乐新增影响且新根帧ID不碰撞`() = inSnapshot {
        val old = NavigationSnapshot(
            activeTabIndex = 3,
            homeIds = listOf(10L), searchIds = listOf(20L), libraryIds = listOf(30L),
            localMusicIds = listOf(40L), downloadsIds = listOf(50L), cloudIds = listOf(60L), myProfileIds = listOf(70L)
        )
        val restored = restoreNavigationSnapshot(old)
        assertEquals(Screen.LocalMusic, restored.activeTab)
        assertEquals(8, restored.liveEntryIds.size)
        restored.openTab(Screen.Music)
        assertTrue(restored.currentEntry.id > 70L)
    }

    @Test
    fun `音乐搜索退出回到原音乐帧并保留最新选择`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.Music)
        nav.updateShowMusicNewWorks(true)
        val musicEntry = nav.currentEntry

        nav.openSearch(autoFocus = true)
        assertTrue(nav.canReturnFromSearch)
        assertTrue(nav.returnFromSearch())

        assertEquals(musicEntry, nav.currentEntry)
        assertTrue(nav.showMusicNewWorks)
        assertFalse(nav.searchAutoFocus)
        assertFalse(nav.canReturnFromSearch)
        assertFalse(nav.returnFromSearch())
    }

    @Test
    fun `音乐搜索结果打开详情后先返回搜索再回音乐`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.Music)
        val musicEntry = nav.currentEntry
        nav.openSearch(autoFocus = true)
        val searchEntry = nav.currentEntry
        nav.openArtist(42L)

        assertFalse(nav.canReturnFromSearch)
        assertFalse(nav.returnFromSearch())
        nav.navigateBack()
        assertEquals(searchEntry, nav.currentEntry)
        assertTrue(nav.canReturnFromSearch)
        nav.navigateBack()
        assertEquals(musicEntry, nav.currentEntry)
    }

    @Test
    fun `底栏搜索主动进入后不会继承音乐搜索返回目标`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.Music)
        nav.openSearch(autoFocus = true)
        nav.openTab(Screen.Search)

        assertEquals(Screen.Search, nav.currentScreen)
        assertFalse(nav.canReturnFromSearch)
        assertFalse(nav.returnFromSearch())
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
    }

    @Test
    fun `离开搜索切其他底栏不会留下未来的音乐返回目标`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.Music)
        nav.openSearch(autoFocus = true)
        nav.navigateTo(Screen.Library)
        nav.openSearch(autoFocus = true)

        assertFalse(nav.canReturnFromSearch)
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
    }

    @Test
    fun `重复音乐搜索往返和重复入口不会污染下一次独立搜索`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.Music)
        val musicEntry = nav.currentEntry
        repeat(3) {
            nav.openSearch(autoFocus = true)
            nav.openSearch(autoFocus = true)
            assertTrue(nav.returnFromSearch())
            assertEquals(musicEntry, nav.currentEntry)
        }
        nav.openTab(Screen.Search)
        nav.openSearch(autoFocus = true)
        assertFalse(nav.canReturnFromSearch)
    }

    @Test
    fun `搜索是默认根页时音乐搜索仍然可以返回原音乐现场`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.updateDefaultRootTab(Screen.Search)
        nav.openTab(Screen.Music)
        nav.openSearch(autoFocus = true)
        assertTrue(nav.canGoBackToHomeAll)

        nav.navigateBack()
        assertEquals(Screen.Music, nav.currentScreen)
    }

    @Test
    fun `搜索来源经过进程快照恢复且保留音乐帧ID和最新`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.Music)
        nav.updateShowMusicNewWorks(true)
        val musicEntry = nav.currentEntry
        nav.openSearch(autoFocus = true)
        nav.openPlaylist(88L, true)
        val encoded = Json.encodeToString(NavigationSnapshot.serializer(), nav.toSnapshot())
        val restored = restoreNavigationSnapshot(Json.decodeFromString(NavigationSnapshot.serializer(), encoded))

        assertEquals(Screen.Playlist(88L, true), restored.currentScreen)
        restored.navigateBack()
        assertTrue(restored.canReturnFromSearch)
        restored.navigateBack()
        assertEquals(musicEntry, restored.currentEntry)
        assertTrue(restored.showMusicNewWorks)
    }

    @Test
    fun `旧快照和无效搜索来源按独立搜索原规则恢复`() = inSnapshot {
        val legacy = restoreNavigationSnapshot(NavigationSnapshot(activeTabScreen = Screen.Search))
        assertFalse(legacy.canReturnFromSearch)
        legacy.navigateBack()
        assertEquals(Screen.Home, legacy.currentScreen)

        val stale = restoreNavigationSnapshot(NavigationSnapshot(
            activeTabScreen = Screen.Library,
            searchReturnTab = Screen.Music
        ))
        stale.openSearch(autoFocus = true)
        assertFalse(stale.canReturnFromSearch)

        val invalid = restoreNavigationSnapshot(NavigationSnapshot(
            activeTabScreen = Screen.Search,
            searchReturnTab = Screen.Home
        ))
        assertFalse(invalid.canReturnFromSearch)
    }
}
