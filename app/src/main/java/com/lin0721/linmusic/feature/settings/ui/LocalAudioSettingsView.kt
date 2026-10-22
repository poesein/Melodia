package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.localmusic.ui.LocalAudioAssociationTransferOptions
import com.lin0721.linmusic.feature.localmusic.ui.LocalMatchingSettingsEntry

/** Local playback policy and durable associations share one discoverable settings page. */
@Composable
fun LocalAudioSettingsView(viewModel: SettingsViewModel, onOpenLibrarySettings: () -> Unit) {
    val preferLocal by viewModel.preferLocalAudio.collectAsStateWithLifecycle()
    val automatic by viewModel.automaticallyMatchLocalAudio.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.lg),
        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)) {
        item("local_playback") {
            SettingsGroupCard("本地优先播放") {
                SettingsSwitchRow("优先使用本地音频", "优先使用已关联文件，不可用时继续尝试下载与在线音源",
                    preferLocal, viewModel::updatePreferLocalAudio)
                HorizontalDivider()
                SettingsSwitchRow("自动匹配本地曲库", "请先扫描本地音乐；唯一可靠匹配可直接用于播放",
                    automatic, viewModel::updateAutomaticallyMatchLocalAudio)
            }
        }
        item("associations") {
            SettingsGroupCard("歌曲与专辑关联") {
                LocalMatchingSettingsEntry()
                HorizontalDivider()
                LocalAudioAssociationTransferOptions()
            }
        }
        item("scan_and_layout") {
            SettingsGroupCard("扫描与本地页面") {
                SettingsRow("本地音乐设置", "扫描目录、授权文件夹、过滤、本地栏目与排序", onOpenLibrarySettings)
            }
        }
    }
}
