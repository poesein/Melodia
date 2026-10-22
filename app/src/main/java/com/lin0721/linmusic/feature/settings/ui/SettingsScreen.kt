package com.lin0721.linmusic.feature.settings.ui

import com.lin0721.linmusic.feature.localmusic.ui.settings.LocalMusicSettingsScreen

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.AdaptiveContentWidth
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaSwitch
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.LocalGlobalOverlayOpen
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// Functional pages keep their existing preference owners; this enum only describes navigation.
enum class SettingsSubMenu(val title: String, val sectionTitles: List<String>, val icon: ImageVector) {
    DISPLAY_LAYOUT("页面与布局", listOf("音乐库页面", "底部页面", "播放页布局", "界面显示"), Icons.Outlined.Dashboard),
    PERSONAL_LIBRARY("个人音乐库", listOf("显示歌单", "布局与栏目"), Icons.Outlined.LibraryMusic),
    ALBUMS("收藏专辑页", listOf("封面布局"), Icons.Outlined.Album),
    LOCAL_AUDIO("本地音乐与匹配", listOf("本地优先播放", "歌曲与专辑关联", "扫描与本地页面"), Icons.Outlined.Folder),
    LOCAL_LIBRARY("本地音乐设置", listOf("扫描目录", "本地页面"), Icons.Outlined.Folder),
    ARTIST_ALBUMS("歌手专辑筛选", listOf("主要录音室专辑"), Icons.Outlined.FilterAlt),
    PLAYBACK_DOWNLOAD("播放与下载", listOf("播放参数", "下载与缓存"), Icons.Outlined.PlayCircleOutline),
    AUDIO_QUALITY("音质", listOf("默认音质", "播放页显示"), Icons.Outlined.HighQuality),
    AUDIO_SOURCES("音源设置", listOf("换源基础设置", "社区扩展源", "远程 UNM 服务"), Icons.AutoMirrored.Outlined.QueueMusic),
    PRIVACY("隐私设置", emptyList(), Icons.Outlined.PrivacyTip),
    STORAGE("储存空间", listOf("存储管理"), Icons.Outlined.Storage),
    NETWORK("网络设置", listOf("网络连接", "网络代理"), Icons.Outlined.Wifi),
    EXTENSIONS("设备集成", listOf("锁屏控制", "车载蓝牙"), Icons.Outlined.Devices),
    LYRICS("歌词设置", listOf("全屏歌词", "悬浮歌词", "外部与系统歌词"), Icons.Outlined.Subtitles),
    ABOUT("关于", listOf("版本更新", "应用说明与协议", "诊断与日志", "特别感谢"), Icons.Outlined.Info);

    val subtitle: String get() = sectionTitles.take(2).joinToString("、")
}

private val MainSettingsGroups = listOf(
    "音乐库与页面" to listOf(SettingsSubMenu.DISPLAY_LAYOUT, SettingsSubMenu.LOCAL_AUDIO, SettingsSubMenu.ARTIST_ALBUMS),
    "播放与音源" to listOf(SettingsSubMenu.PLAYBACK_DOWNLOAD, SettingsSubMenu.AUDIO_QUALITY, SettingsSubMenu.AUDIO_SOURCES, SettingsSubMenu.LYRICS),
    "系统与应用" to listOf(SettingsSubMenu.EXTENSIONS, SettingsSubMenu.NETWORK, SettingsSubMenu.PRIVACY, SettingsSubMenu.STORAGE, SettingsSubMenu.ABOUT)
)

private fun SettingsSubMenu.parentMenu(): SettingsSubMenu? = when (this) {
    SettingsSubMenu.PERSONAL_LIBRARY, SettingsSubMenu.ALBUMS -> SettingsSubMenu.DISPLAY_LAYOUT
    SettingsSubMenu.LOCAL_LIBRARY -> SettingsSubMenu.LOCAL_AUDIO
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var activeSubMenu by rememberSaveable { mutableStateOf<SettingsSubMenu?>(null) }

    // 全局浮层开着时让位，避免抢先吞掉本该用来关浮层的返回事件
    BackHandler(enabled = activeSubMenu != null && !LocalGlobalOverlayOpen.current) {
        activeSubMenu = activeSubMenu?.parentMenu()
    }
    
    // 监听 Toast 提示消息
    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collectLatest { msg ->
            ToastManager.showToast(msg)
        }
    }

    // Reuse the local library's own screen and ViewModel rather than duplicating its scanner UI.
    if (activeSubMenu == SettingsSubMenu.LOCAL_LIBRARY) {
        LocalMusicSettingsScreen(onBack = { activeSubMenu = SettingsSubMenu.LOCAL_AUDIO })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeSubMenu?.title ?: "设置",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    MelodiaIconButton(
                        onClick = {
                            if (activeSubMenu != null) {
                                activeSubMenu = activeSubMenu?.parentMenu()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        AdaptiveContentWidth(
            modifier = Modifier.padding(innerPadding)
        ) {
            // 使用 AnimatedContent 实现主菜单和子菜单的平滑切换动画
            AnimatedContent(
                targetState = activeSubMenu,
                transitionSpec = {
                    if (targetState != null) {
                        // 进入子菜单：从右往左滑入
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        // 返回主菜单：从左向右滑入
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                label = "SettingsMenuTransition"
            ) { subMenu ->
                if (subMenu == null) {
                    // 主设置菜单
                    MainSettingsMenu(
                        onNavigate = { activeSubMenu = it },
                        onBack = onBack,
                        viewModel = viewModel
                    )
                } else {
                    // 各分类子菜单内容
                    SubMenuContent(
                        subMenu = subMenu,
                        viewModel = viewModel,
                        context = context,
                        onNavigate = { activeSubMenu = it }
                    )
                }
            }
        }
    }
}

// 主设置列表菜单
@Composable
private fun MainSettingsMenu(
    onNavigate: (SettingsSubMenu) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MelodiaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp, top = 8.dp)
    ) {
        MainSettingsGroups.forEach { (groupTitle, menus) -> item(key = groupTitle) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = groupTitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = MelodiaSpacing.xs, bottom = MelodiaSpacing.sm)
                )
                menus.forEach { item ->
                    SettingsMainMenuRow(
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        onClick = { onNavigate(item) }
                    )
                }
            }
        } }

        // 底部账户操作按钮组
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MelodiaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 退出登录按钮
                MelodiaButton(
                    onClick = {
                        viewModel.executeLogout {
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(PillRadiusLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "退出登录",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// 统一子菜单内容分发器
@Composable
private fun SubMenuContent(
    subMenu: SettingsSubMenu,
    viewModel: SettingsViewModel,
    context: Context,
    onNavigate: (SettingsSubMenu) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MelodiaSpacing.md)
    ) {
        when (subMenu) {
            SettingsSubMenu.DISPLAY_LAYOUT -> DisplaySettingsView(viewModel, onNavigate)
            SettingsSubMenu.PERSONAL_LIBRARY -> com.lin0721.linmusic.feature.personallibrary.PersonalLibrarySettingsView()
            SettingsSubMenu.ALBUMS -> com.lin0721.linmusic.feature.albumlibrary.CollectedAlbumsSettingsView()
            SettingsSubMenu.LOCAL_AUDIO -> LocalAudioSettingsView(viewModel, onOpenLibrarySettings = { onNavigate(SettingsSubMenu.LOCAL_LIBRARY) })
            SettingsSubMenu.LOCAL_LIBRARY -> Unit // Rendered above the shared settings Scaffold.
            SettingsSubMenu.ARTIST_ALBUMS -> ArtistAlbumsSettingsView(viewModel)
            SettingsSubMenu.PLAYBACK_DOWNLOAD -> PlaybackDownloadSettingsView(viewModel)
            SettingsSubMenu.AUDIO_QUALITY -> AudioQualitySettingsView(viewModel)
            SettingsSubMenu.AUDIO_SOURCES -> AudioSourcesSettingsView(viewModel)
            SettingsSubMenu.PRIVACY -> PrivacySettingsView(viewModel)
            SettingsSubMenu.STORAGE -> StorageSettingsView(viewModel, context)
            SettingsSubMenu.NETWORK -> NetworkSettingsView(viewModel)
            SettingsSubMenu.EXTENSIONS -> ExtensionsSettingsView(viewModel)
            SettingsSubMenu.LYRICS -> LyricsSettingsView(viewModel)
            SettingsSubMenu.ABOUT -> AboutSettingsView(viewModel)
        }
    }
}

// ─── 辅助卡片及布局小组件 (不带 private，以便同包子模块访问) ───

@Composable
fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = MelodiaSpacing.xs, bottom = MelodiaSpacing.sm)
        )
        Column(content = content)
    }
}

@Composable
private fun SettingsMainMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(MelodiaSpacing.md))
        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.sm)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.sm)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.md)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        MelodiaSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

fun getBindingIcon(type: Int): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        1 -> Icons.Default.PhoneAndroid
        10 -> Icons.Default.ChatBubbleOutline
        20 -> Icons.Default.AccountCircle
        else -> Icons.Default.Link
    }
}
