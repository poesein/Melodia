package com.lin0721.linmusic.core.preferences

// 移动端及大屏底部导航栏候选项目
enum class BottomBarItem(val key: String, val title: String, val isRoute: Boolean) {
    HOME("home", "主页", true),
    MUSIC("music", "音乐", true),
    SEARCH("search", "搜索", true),
    LIBRARY("library", "音乐库", true),
    PERSONAL_LIBRARY("personal_library", "个人音乐库", true),
    CREATE("create", "创建", false),
    LOCAL_MUSIC("local_music", "本地音乐", true),
    DOWNLOADS("downloads", "下载管理", true),
    CLOUD("cloud", "我的云盘", true),
    PROFILE("profile", "个人主页", true);

    companion object {
        fun fromKey(key: String): BottomBarItem? = when (key) {
            "account" -> PROFILE
            else -> entries.firstOrNull { it.key == key }
        }
    }
}

data class BottomBarItemSetting(
    val item: BottomBarItem,
    val visible: Boolean
)

// 底栏布局持久化与校验逻辑：`home:1,search:1,library:1,create:1,local_music:0,...`
object BottomBarLayout {
    const val MIN_VISIBLE_ITEMS = 2
    const val MAX_VISIBLE_ITEMS = 6

    val DEFAULT: List<BottomBarItemSetting> = listOf(
        BottomBarItemSetting(BottomBarItem.HOME, true),
        BottomBarItemSetting(BottomBarItem.MUSIC, true),
        BottomBarItemSetting(BottomBarItem.SEARCH, true),
        BottomBarItemSetting(BottomBarItem.LIBRARY, true),
        BottomBarItemSetting(BottomBarItem.PERSONAL_LIBRARY, true),
        BottomBarItemSetting(BottomBarItem.CREATE, true),
        BottomBarItemSetting(BottomBarItem.LOCAL_MUSIC, false),
        BottomBarItemSetting(BottomBarItem.DOWNLOADS, false),
        BottomBarItemSetting(BottomBarItem.CLOUD, false),
        BottomBarItemSetting(BottomBarItem.PROFILE, false)
    )

    fun encode(layout: List<BottomBarItemSetting>): String =
        normalize(layout).joinToString(",") { "${it.item.key}:${if (it.visible) 1 else 0}" }

    fun decode(raw: String?, defaultShowCreate: Boolean? = null): List<BottomBarItemSetting> {
        if (raw.isNullOrBlank()) {
            return if (defaultShowCreate != null) {
                DEFAULT.map {
                    if (it.item == BottomBarItem.CREATE) it.copy(visible = defaultShowCreate) else it
                }
            } else {
                DEFAULT
            }
        }
        val parsed = raw.split(",").mapNotNull { token ->
            val parts = token.trim().split(":", limit = 2)
            val item = BottomBarItem.fromKey(parts[0].trim()) ?: return@mapNotNull null
            BottomBarItemSetting(item, visible = parts.getOrNull(1)?.trim() == "1")
        }
        return normalize(migratePersonalLibraryEntry(migrateMusicEntry(parsed)))
    }

    private fun migratePersonalLibraryEntry(layout: List<BottomBarItemSetting>): List<BottomBarItemSetting> {
        // Only introduce the new route once. An explicitly hidden entry stays hidden.
        if (layout.any { it.item == BottomBarItem.PERSONAL_LIBRARY }) return layout
        val migrated = layout.toMutableList()
        val libraryIndex = migrated.indexOfFirst { it.item == BottomBarItem.LIBRARY }
        migrated.add(if (libraryIndex >= 0) libraryIndex + 1 else migrated.size,
            BottomBarItemSetting(BottomBarItem.PERSONAL_LIBRARY, layout.count { it.visible } < MAX_VISIBLE_ITEMS))
        return migrated
    }

    private fun migrateMusicEntry(layout: List<BottomBarItemSetting>): List<BottomBarItemSetting> {
        if (layout.any { it.item == BottomBarItem.MUSIC }) return layout
        val migrated = layout.toMutableList()
        val homeIndex = migrated.indexOfFirst { it.item == BottomBarItem.HOME }
        // Preserve all selected items in a full custom layout; the new entry can
        // be enabled explicitly in settings. Legacy defaults have room for it.
        migrated.add(if (homeIndex >= 0) homeIndex + 1 else 0,
            BottomBarItemSetting(BottomBarItem.MUSIC, layout.count { it.visible } < 5))
        return migrated
    }

    // 去重、补齐缺失项并修正可见数量上下限
    fun normalize(layout: List<BottomBarItemSetting>): List<BottomBarItemSetting> {
        val distinct = layout.distinctBy { it.item }
        val missing = BottomBarItem.entries
            .filter { item -> distinct.none { it.item == item } }
            .map { BottomBarItemSetting(it, visible = false) }
        val merged = distinct + missing

        var visibleCount = 0
        val bounded = merged.map { setting ->
            if (setting.visible) {
                if (visibleCount < MAX_VISIBLE_ITEMS) {
                    visibleCount++
                    setting
                } else {
                    setting.copy(visible = false)
                }
            } else {
                setting
            }
        }

        // 若可见项少于下限，从前到后自动开启补足到 MIN_VISIBLE_ITEMS
        if (visibleCount < MIN_VISIBLE_ITEMS) {
            val adjusted = bounded.toMutableList()
            for (i in adjusted.indices) {
                if (!adjusted[i].visible) {
                    adjusted[i] = adjusted[i].copy(visible = true)
                    visibleCount++
                    if (visibleCount >= MIN_VISIBLE_ITEMS) break
                }
            }
            return adjusted
        }

        return bounded
    }
}
