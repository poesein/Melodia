package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

/** Device integrations stay separate from library layout and album filtering. */
@Composable
fun ExtensionsSettingsView(viewModel: SettingsViewModel) {
    val showLockscreen by viewModel.showLockscreen.collectAsStateWithLifecycle()
    val carMode by viewModel.carMode.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.lg),
        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        item("lockscreen") {
            SettingsGroupCard("锁屏控制") {
                SettingsSwitchRow("启用系统锁屏显示", "在锁屏界面展示播放控制器与歌词面板",
                    showLockscreen, viewModel::updateShowLockscreen)
            }
        }
        item("car") {
            SettingsGroupCard("车载蓝牙") {
                SettingsSwitchRow("车载模式蓝牙自动启动", "连接车载蓝牙设备时自动恢复媒体播放",
                    carMode, viewModel::updateCarMode)
            }
        }
    }
}
