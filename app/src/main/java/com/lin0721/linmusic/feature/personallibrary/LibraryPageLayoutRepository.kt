package com.lin0721.linmusic.feature.personallibrary

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.musicPageLayouts by preferencesDataStore(name = "music_page_layouts")

/** Display-only preferences shared by the two page hosts, separate from scan/filter/account data. */
class LibraryPageLayoutRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.musicPageLayouts)
    private val json = Json { ignoreUnknownKeys = true }
    private fun key(page: LibraryPage) = stringPreferencesKey("${page.key}_layout")
    private fun decode(raw: String?, page: LibraryPage): LibraryPageLayout {
        val layout = if (raw == null) LibraryPageLayout() else try { json.decodeFromString<LibraryPageLayout>(raw) }
        catch (error: Exception) { throw IOException("布局设置无法读取，原设置已保留", error) }
        if (layout.version != 1) throw IOException("布局设置版本无法识别，原设置已保留")
        return layout.normalized(page)
    }
    fun observe(page: LibraryPage) = store.data.map { decode(it[key(page)], page) }.distinctUntilChanged()
    suspend fun update(page: LibraryPage, change: (LibraryPageLayout) -> LibraryPageLayout) {
        store.edit { values -> values[key(page)] = json.encodeToString(change(decode(values[key(page)], page)).normalized(page)) }
    }
}
