package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset

@Composable
fun ArtistAlbumsSettingsView(viewModel: SettingsViewModel) {
    val enabled by viewModel.artistCatalogDefaultEnabled.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp,
        bottom = LocalBottomOverlayInset.current + 16.dp)) {
        item("catalog_filter") {
            SettingsGroupCard("主要录音室专辑") {
                SettingsSwitchRow(
                    title = "默认启用资料库筛选",
                    subtitle = "歌手页的“只显示录音室专辑”内可手动修正或恢复全部；已保存的修正会保留",
                    checked = enabled,
                    onCheckedChange = viewModel::updateArtistCatalogDefaultEnabled
                )
            }
        }
    }
}
