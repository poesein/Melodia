package com.lin0721.linmusic.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarLayoutTest {

    @Test
    fun `空值或空白串回退默认布局`() {
        assertEquals(BottomBarLayout.DEFAULT, BottomBarLayout.decode(null))
        assertEquals(BottomBarLayout.DEFAULT, BottomBarLayout.decode("   "))
    }

    @Test
    fun `空值时尊重旧版创建入口配置`() {
        val decodedWithCreateFalse = BottomBarLayout.decode(null, defaultShowCreate = false)
        val createSetting = decodedWithCreateFalse.first { it.item == BottomBarItem.CREATE }
        assertEquals(false, createSetting.visible)
    }

    @Test
    fun `编码后再解码保持顺序与显隐`() {
        val layout = listOf(
            BottomBarItemSetting(BottomBarItem.SEARCH, true),
            BottomBarItemSetting(BottomBarItem.LOCAL_MUSIC, true),
            BottomBarItemSetting(BottomBarItem.HOME, false),
            BottomBarItemSetting(BottomBarItem.LIBRARY, false),
            BottomBarItemSetting(BottomBarItem.CREATE, false),
            BottomBarItemSetting(BottomBarItem.DOWNLOADS, false),
            BottomBarItemSetting(BottomBarItem.CLOUD, false),
            BottomBarItemSetting(BottomBarItem.PROFILE, false),
            BottomBarItemSetting(BottomBarItem.MUSIC, false),
            BottomBarItemSetting(BottomBarItem.PERSONAL_LIBRARY, false)
        )
        val encoded = BottomBarLayout.encode(layout)
        assertEquals(layout, BottomBarLayout.decode(encoded))
    }

    @Test
    fun `可见数量少于2个时自动补足下限`() {
        val layout = listOf(
            BottomBarItemSetting(BottomBarItem.LOCAL_MUSIC, true),
            BottomBarItemSetting(BottomBarItem.HOME, false),
            BottomBarItemSetting(BottomBarItem.SEARCH, false),
            BottomBarItemSetting(BottomBarItem.LIBRARY, false),
            BottomBarItemSetting(BottomBarItem.CREATE, false),
            BottomBarItemSetting(BottomBarItem.DOWNLOADS, false),
            BottomBarItemSetting(BottomBarItem.CLOUD, false),
            BottomBarItemSetting(BottomBarItem.PROFILE, false)
        )
        val normalized = BottomBarLayout.normalize(layout)
        val visibleCount = normalized.count { it.visible }
        assertTrue(visibleCount >= BottomBarLayout.MIN_VISIBLE_ITEMS)
    }

    @Test
    fun `可见数量超过6个时自动截断至上限`() {
        val layout = BottomBarItem.entries.map { BottomBarItemSetting(it, visible = true) }
        val normalized = BottomBarLayout.normalize(layout)
        val visibleCount = normalized.count { it.visible }
        assertEquals(BottomBarLayout.MAX_VISIBLE_ITEMS, visibleCount)
    }

    @Test
    fun `旧默认布局升级后音乐入口位于主页之后且默认可见`() {
        val old = "home:1,search:1,library:1,create:1,local_music:0,downloads:0,cloud:0,profile:0"
        val migrated = BottomBarLayout.decode(old)
        assertEquals(listOf(BottomBarItem.HOME, BottomBarItem.MUSIC, BottomBarItem.SEARCH, BottomBarItem.LIBRARY, BottomBarItem.PERSONAL_LIBRARY, BottomBarItem.CREATE),
            migrated.filter { it.visible }.map { it.item })
    }

    @Test
    fun `已满五项的自定义布局迁移不替换用户选项`() {
        val old = "local_music:1,downloads:1,cloud:1,profile:1,search:1,home:0,library:0,create:0"
        val migrated = BottomBarLayout.decode(old)
        assertEquals(listOf(BottomBarItem.LOCAL_MUSIC, BottomBarItem.DOWNLOADS, BottomBarItem.CLOUD, BottomBarItem.PROFILE, BottomBarItem.SEARCH, BottomBarItem.PERSONAL_LIBRARY),
            migrated.filter { it.visible }.map { it.item })
        assertEquals(false, migrated.first { it.item == BottomBarItem.MUSIC }.visible)
    }

    @Test
    fun `明确隐藏音乐入口后不会在下次读取时重新打开`() {
        val hidden = BottomBarLayout.DEFAULT.map { if (it.item == BottomBarItem.MUSIC) it.copy(visible = false) else it }
        assertEquals(hidden, BottomBarLayout.decode(BottomBarLayout.encode(hidden)))
    }

    @Test
    fun `旧自定义排序显隐在增加音乐项后保持原设置`() {
        val old = "search:1,home:0,library:1,create:0,local_music:0,downloads:0,cloud:0,profile:0"
        val migrated = BottomBarLayout.decode(old)
        assertEquals(listOf(BottomBarItem.SEARCH, BottomBarItem.HOME, BottomBarItem.LIBRARY, BottomBarItem.CREATE, BottomBarItem.LOCAL_MUSIC, BottomBarItem.DOWNLOADS, BottomBarItem.CLOUD, BottomBarItem.PROFILE),
            migrated.filter { it.item != BottomBarItem.MUSIC && it.item != BottomBarItem.PERSONAL_LIBRARY }.map { it.item })
        assertEquals(false, migrated.first { it.item == BottomBarItem.HOME }.visible)
        assertEquals(false, migrated.first { it.item == BottomBarItem.CREATE }.visible)
    }

    @Test fun personalLibraryCanBeFirstOrHiddenWithoutLosingTheOriginalLibrary() {
        val reordered = listOf(BottomBarItemSetting(BottomBarItem.PERSONAL_LIBRARY, true)) +
            BottomBarLayout.DEFAULT.filterNot { it.item == BottomBarItem.PERSONAL_LIBRARY }
        assertEquals(reordered, BottomBarLayout.decode(BottomBarLayout.encode(reordered)))
        val hidden = reordered.map { if (it.item == BottomBarItem.PERSONAL_LIBRARY) it.copy(visible = false) else it }
        assertEquals(hidden, BottomBarLayout.decode(BottomBarLayout.encode(hidden)))
        assertTrue(hidden.first { it.item == BottomBarItem.LIBRARY }.visible)
    }
}
