package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePreferencesStrictTest {
    @Test fun `旧配置缺严格键时默认关闭且不影响原有音源设置`() = runBlocking {
        val store = InMemoryPreferencesStore()
        val old = SourcePreferences(store)
        old.saveFallbackEnabled(true)
        old.saveLxPluginEnabled(true)
        old.saveUnmServerUrl("https://unm.invalid")
        val updated = SourcePreferences(store)
        assertFalse(updated.strictFallbackEnabled.first())
        assertTrue(updated.fallbackEnabled.first())
        assertTrue(updated.lxPluginEnabled.first())
        assertEquals("https://unm.invalid", updated.unmServerUrl.first())
    }

    @Test fun `严格开关独立持久化并可安全关闭恢复原设置`() = runBlocking {
        val store = InMemoryPreferencesStore()
        val prefs = SourcePreferences(store)
        prefs.saveCommunityPriority(true)
        prefs.saveStrictFallbackEnabled(true)
        assertTrue(SourcePreferences(store).strictFallbackEnabled.first())
        prefs.saveStrictFallbackEnabled(false)
        assertFalse(SourcePreferences(store).strictFallbackEnabled.first())
        assertTrue(prefs.communityPriority.first())
    }
}
