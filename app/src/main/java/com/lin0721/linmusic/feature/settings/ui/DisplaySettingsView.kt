package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.preferences.UiScale
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.feature.player.ui.LyricCapsuleSlider

private val PlayerPageModes = listOf(
    SettingsPreferences.PLAYER_PAGE_MODE_AUTO to "自动",
    SettingsPreferences.PLAYER_PAGE_MODE_FULLSCREEN to "全屏式",
    SettingsPreferences.PLAYER_PAGE_MODE_SIDE to "侧边小窗式"
)

/** Navigation and appearance only: each feature retains its own persisted settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsView(viewModel: SettingsViewModel, onNavigate: (SettingsSubMenu) -> Unit) {
    val bottomBarLayout by viewModel.bottomBarLayout.collectAsStateWithLifecycle()
    val bottomBarIconOnly by viewModel.bottomBarIconOnly.collectAsStateWithLifecycle()
    val startInLibrary by viewModel.startInLibrary.collectAsStateWithLifecycle()
    val playerPageMode by viewModel.playerPageMode.collectAsStateWithLifecycle()
    val sidePlayerPinned by viewModel.sidePlayerPinned.collectAsStateWithLifecycle()
    val tabletImmersiveMode by viewModel.tabletImmersiveMode.collectAsStateWithLifecycle()
    val uiScalePercent by viewModel.uiScalePercent.collectAsStateWithLifecycle()
    val isExpanded = LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded
    var showBottomBarSheet by rememberSaveable { mutableStateOf(false) }
    var showPlayerModeSheet by rememberSaveable { mutableStateOf(false) }
    val playerModeLabel = PlayerPageModes.firstOrNull { it.first == playerPageMode }?.second ?: "自动"
    val playerModeSubtitle = when (playerPageMode) {
        SettingsPreferences.PLAYER_PAGE_MODE_FULLSCREEN -> "点击展开，返回直接收起"
        SettingsPreferences.PLAYER_PAGE_MODE_SIDE -> "播放页固定在右侧，与内容并排"
        else -> "竖屏全屏式，横屏侧边小窗式"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.lg),
        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        item("library_pages") {
            SettingsGroupCard("音乐库页面") {
                SettingsRow("个人音乐库", "栏目排序与显隐、显示歌单、列表和封面布局") {
                    onNavigate(SettingsSubMenu.PERSONAL_LIBRARY)
                }
                HorizontalDivider()
                SettingsRow("收藏专辑页", "封面列数与浏览布局") { onNavigate(SettingsSubMenu.ALBUMS) }
            }
        }
        item("bottom_pages") {
            SettingsGroupCard("底部页面") {
                val summary = remember(bottomBarLayout) {
                    bottomBarLayout.filter { it.visible }.joinToString("、") { it.item.title }
                }
                SettingsRow("底部页面排序与显示", "当前启用：$summary") { showBottomBarSheet = true }
                HorizontalDivider()
                SettingsSwitchRow(
                    title = "启动默认打开音乐库",
                    subtitle = "冷启动时直接进入音乐库页面",
                    checked = startInLibrary,
                    onCheckedChange = viewModel::updateStartInLibrary
                )
            }
        }
        item("player_layout") {
            SettingsGroupCard("播放页布局") {
                SettingsRow("播放页模式：$playerModeLabel", playerModeSubtitle) { showPlayerModeSheet = true }
                if (playerPageMode != SettingsPreferences.PLAYER_PAGE_MODE_FULLSCREEN) {
                    HorizontalDivider()
                    SettingsSwitchRow(
                        title = "侧边播放页钉住",
                        subtitle = "关闭后按返回键直接收起侧边播放页",
                        checked = sidePlayerPinned,
                        onCheckedChange = viewModel::updateSidePlayerPinned
                    )
                }
                if (isExpanded) {
                    HorizontalDivider()
                    SettingsSwitchRow(
                        title = "全局沉浸",
                        subtitle = "隐藏系统状态栏与导航栏，边缘滑动呼出",
                        checked = tabletImmersiveMode,
                        onCheckedChange = viewModel::updateTabletImmersiveMode
                    )
                }
            }
        }
        item("scale") {
            SettingsGroupCard("界面显示") {
                // Applying a new scale while dragging moves the slider under the pointer.
                var pendingPercent by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(uiScalePercent) { pendingPercent = null }
                val percent = pendingPercent ?: uiScalePercent
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("界面缩放", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                        Text("$percent%", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    Text("整体放大界面，松手后生效", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    LyricCapsuleSlider(
                        value = percent,
                        onValueChange = { pendingPercent = it },
                        valueRange = UiScale.MIN_PERCENT.toFloat()..UiScale.MAX_PERCENT.toFloat(),
                        startLabel = "${UiScale.MIN_PERCENT}%",
                        endLabel = "${UiScale.MAX_PERCENT}%",
                        onValueChangeFinished = { pendingPercent?.let(viewModel::updateUiScalePercent) }
                    )
                }
            }
        }
    }

    if (showBottomBarSheet) {
        BottomBarCustomizationSheet(
            layout = bottomBarLayout, iconOnly = bottomBarIconOnly,
            onLayoutChange = viewModel::updateBottomBarLayout,
            onIconOnlyChange = viewModel::updateBottomBarIconOnly,
            onDismiss = { showBottomBarSheet = false }
        )
    }
    if (showPlayerModeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlayerModeSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            shape = BottomSheetShape, dragHandle = { MelodiaDragHandle() }
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)) {
                Text("选择播放页模式", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = MelodiaSpacing.md))
                PlayerPageModes.forEach { (key, label) ->
                    val selected = playerPageMode == key
                    Row(Modifier.fillMaxWidth().clickable {
                        viewModel.updatePlayerPageMode(key)
                        showPlayerModeSheet = false
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp)
                        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
