package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.ArtistPickerEntry
import com.lin0721.linmusic.core.ui.components.ArtistPickerSheet
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import com.lin0721.linmusic.core.comment.ui.CommentFullScreen
import com.lin0721.linmusic.core.comment.domain.CommentFloorState
import com.lin0721.linmusic.core.comment.ui.CommentFloorScreen
import com.lin0721.linmusic.core.model.CommentItem
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.LocalGlobalOverlayOpen
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.feature.localmusic.ui.LocalAlbumAssociationViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalAlbumAssociationHost
import com.lin0721.linmusic.feature.artist.ui.catalog.AlbumArtistCorrectionHost
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.isLikedSongsPlaylist
import com.lin0721.linmusic.core.model.isOwnLikedSongs
import com.lin0721.linmusic.core.ui.components.DraggableSongRow
import com.lin0721.linmusic.core.ui.components.SelectionAction
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.components.TrackSelectionUi
import com.lin0721.linmusic.core.ui.theme.melodiaStatusBarTopPadding
import java.util.Collections
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: Long,
    isAlbum: Boolean = false,
    viewModel: PlaylistViewModel = koinViewModel(),
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onNavigateToProfile: (Long) -> Unit = {},
    sortViewModel: PlaylistTrackSortViewModel = koinViewModel(key = "playlist-track-sort:${if (isAlbum) "album" else "playlist"}:$playlistId")
) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val totalDurationMs by viewModel.totalDurationMs.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playerManager.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.playWhenReady.collectAsStateWithLifecycle()
    val playMode by viewModel.playerManager.playMode.collectAsStateWithLifecycle()
    val playSource by viewModel.playerManager.playSource.collectAsStateWithLifecycle()
    val currentQueue by viewModel.playerManager.queue.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val unplayableIds by viewModel.unplayableIds.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val userProfile  by viewModel.userProfile.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val historyRecommendState by viewModel.historyRecommendState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val addMusicSearchQuery by viewModel.addMusicSearchQuery.collectAsStateWithLifecycle()
    val addMusicSearchState by viewModel.addMusicSearchState.collectAsStateWithLifecycle()
    val isSavingInfo by viewModel.isSavingInfo.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val pendingLikeIds by viewModel.pendingLikeIds.collectAsStateWithLifecycle()
    val albumAssociation: LocalAlbumAssociationViewModel? = if (isAlbum) koinViewModel() else null
    albumAssociation?.let { LocalAlbumAssociationHost(it) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    fun sharePlaylistOrAlbum(name: String, id: Long) {
        val resourceLabel = if (isAlbum) "专辑" else "歌单"
        val urlPath = if (isAlbum) "album" else "playlist"
        val shareText = "$name https://music.163.com/$urlPath?id=$id"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "分享$resourceLabel"))
    }
    // 裁剪结果只暂存本地 uri 做预览，等用户点「保存」才读字节上传，
    // 与名称/简介保持同一套提交时机
    var pendingCoverUri by remember { mutableStateOf<Uri?>(null) }
    val coverCropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (!result.isSuccessful) {
            result.error?.message?.let { com.lin0721.linmusic.core.ui.components.ToastManager.showToast(it) }
            return@rememberLauncherForActivityResult
        }
        pendingCoverUri = result.uriContent
    }
    val launchCoverCropper = {
        coverCropLauncher.launch(
            CropImageContractOptions(
                uri = null,
                cropImageOptions = CropImageOptions(
                    imageSourceIncludeCamera = false,
                    imageSourceIncludeGallery = true,
                    cropShape = CropImageView.CropShape.RECTANGLE,
                    fixAspectRatio = true,
                    aspectRatioX = 1,
                    aspectRatioY = 1,
                    outputCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG,
                    outputCompressQuality = 90,
                    outputRequestWidth = 1024,
                    outputRequestHeight = 1024,
                    outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                    // 默认工具栏是浅色主题、图标色跟未设置的深色背景撞色，看起来像没有确认按钮，这里强制统一成深色+白图标
                    activityBackgroundColor = android.graphics.Color.BLACK,
                    toolbarColor = android.graphics.Color.BLACK,
                    toolbarTintColor = android.graphics.Color.WHITE,
                    toolbarTitleColor = android.graphics.Color.WHITE,
                    toolbarBackButtonColor = android.graphics.Color.WHITE,
                    activityMenuIconColor = android.graphics.Color.WHITE
                )
            )
        )
    }

    var showLoginSheet by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showCommentFloor by remember { mutableStateOf(false) }
    val composerState by viewModel.composerState.collectAsStateWithLifecycle()
    val floorState by viewModel.floorState.collectAsStateWithLifecycle()
    var showMoreMenuSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var albumArtistCorrection by remember(playlistId, isAlbum) { mutableStateOf<PlaylistDetail?>(null) }
    var artistPickerEntries by remember { mutableStateOf<List<ArtistPickerEntry>>(emptyList()) }
    var showImportTargetSheet by remember { mutableStateOf(false) }
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddMusicSheet by remember { mutableStateOf(false) }
    var showDownloadQualitySheet by remember { mutableStateOf(false) }
    var pendingDownloadTrack by remember { mutableStateOf<Track?>(null) }
    var showBatchTargetSheet by remember { mutableStateOf(false) }
    var showBatchDownloadSheet by remember { mutableStateOf(false) }
    var showBatchRemoveConfirm by remember { mutableStateOf(false) }
    var showBatchUnlikeConfirm by remember { mutableStateOf(false) }
    var isReorderMode by remember { mutableStateOf(false) }
    val sortState by sortViewModel.state.collectAsStateWithLifecycle()
    val sortTarget = PlaylistSortTarget(playlistId, isAlbum)
    val activeSortScope = sortState.scope
    val sortReady = sortState.loaded && activeSortScope != null && activeSortScope.target == sortTarget &&
        activeSortScope.accountId == userProfile?.uid?.takeIf { it > 0 }
    val trackSort = sortState.sort
    // Don't create the paged list until its persisted direction is known. A brief default
    // render would show the wrong order and may request pages before restoring reverse mode.
    val visibleUiState = if (uiState is PlaylistUiState.Success && !sortReady) PlaylistUiState.Loading else uiState
    // A full load may finish after the user changes direction; read the latest selection then.
    val playbackSort by rememberUpdatedState(trackSort)
    var isSavingOrder by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    val reorderedTracks = remember { mutableStateListOf<Track>() }
    var selectedHistoryDate by remember { mutableStateOf("今天") }

    // reorderedTracks 是引用不变的 SnapshotStateList，remember(keys) 感知不到其内容变化，必须用 derivedStateOf 才能正确响应拖拽
    val isOrderChanged by remember {
        derivedStateOf {
            val current = (uiState as? PlaylistUiState.Success)?.playlist?.tracks.orEmpty()
            reorderedTracks.isNotEmpty() && reorderedTracks != current
        }
    }

    // 全局浮层开着时让位，避免抢先吞掉本该用来关浮层的返回事件
    val isGlobalOverlayOpen = LocalGlobalOverlayOpen.current

    BackHandler(enabled = isReorderMode && !isGlobalOverlayOpen) {
        if (!isSavingOrder) {
            if (isOrderChanged) {
                showDiscardConfirmDialog = true
            } else {
                isReorderMode = false
            }
        }
    }

    BackHandler(enabled = showCommentsSheet && !isGlobalOverlayOpen) {
        showCommentsSheet = false
    }

    BackHandler(enabled = showCommentFloor && !isGlobalOverlayOpen) {
        showCommentFloor = false
    }

    LaunchedEffect(historyRecommendState.selectedDate) {
        selectedHistoryDate = historyRecommendState.selectedDate ?: "今天"
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { com.lin0721.linmusic.core.ui.components.ToastManager.showToast(it) }
    }
    LaunchedEffect(sortViewModel, playlistId, isAlbum) { sortViewModel.bind(sortTarget) }
    LaunchedEffect(sortState.loaded, sortState.error) {
        if (sortState.loaded) sortState.error?.let { com.lin0721.linmusic.core.ui.components.ToastManager.showToast(it) }
    }
    LaunchedEffect(playlistId, isAlbum) {
        viewModel.loadPlaylistIfNeeded(playlistId, isAlbum)
        if (playlistId == -1L) {
            viewModel.loadHistoryDates()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = visibleUiState) {
            is PlaylistUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val sortError = sortState.error?.takeUnless { sortState.loaded }
                    if (sortError == null) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    else Column(Modifier.padding(MelodiaSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(sortError, color = MaterialTheme.colorScheme.onSurface)
                        TextButton(onClick = sortViewModel::retry) { Text("重新读取排序") }
                        activeSortScope?.let { scope ->
                            TextButton(enabled = !sortState.saving, onClick = { sortViewModel.reset(scope) }) {
                                Text(if (isAlbum) "重置此专辑排序" else "重置此歌单排序")
                            }
                        }
                        TextButton(onClick = onBack) { Text("返回") }
                    }
                }
            is PlaylistUiState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(MelodiaSpacing.sm))
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(MelodiaSpacing.md))
                        MelodiaButton(onClick = { viewModel.loadPlaylist(playlistId, isAlbum) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Text("重试", color = MaterialTheme.colorScheme.onPrimary)
                        }
                        MelodiaTextButton(onClick = onBack) { Text("返回", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            is PlaylistUiState.Success -> {
                val profile = userProfile
                val isLikedSongsPlaylistView = profile != null &&
                    state.playlist.isOwnLikedSongs(profile.uid)
                val isOwnedPlaylist = profile != null &&
                    state.playlist.id > 0L &&
                    state.playlist.creator?.userId == profile.uid &&
                    !isLikedSongsPlaylistView
                val isShuffleActive = playMode == PlayMode.SHUFFLE
                val expectedKind = if (isAlbum) com.lin0721.linmusic.core.player.PlaySource.Kind.ALBUM else com.lin0721.linmusic.core.player.PlaySource.Kind.PLAYLIST
                val isThisPlaylistContext = playSource?.kind == expectedKind && playSource?.id == state.playlist.id
                val orderedIds = remember(state.playlist.tracks, trackSort) { trackSort.sort(state.playlist.tracks).map { it.id } }
                val queueIds = remember(currentQueue) { currentQueue.map { it.songId } }
                val isThisOrderedQueue = isThisPlaylistContext && com.lin0721.linmusic.feature.playlist.presentation.playlistQueueMatches(
                    queueIds, orderedIds, isShuffleActive,
                    allowLoadedPrefix = state.hasMoreTracks && trackSort == PlaylistTrackSort())
                val isCurrentlyPlayingThis = isThisOrderedQueue && isPlaying
                // A refreshed/reloaded playlist may become paged again while reverse is still on.
                // Key by server IDs, not each loaded page, to avoid restarting or retrying endlessly.
                LaunchedEffect(playlistId, isAlbum, trackSort.reversed, state.playlist.trackIds) {
                    if (trackSort.reversed && state.hasMoreTracks) viewModel.ensureAllTracksLoaded {}
                }

                // 进入/退出重排模式复用 MelodiaNavHost 的整屏切换动效（滑入+淡入/滑出+淡出），
                // 不改动内部结构：分支条件从外层 isReorderMode 换成 lambda 参数 reordering，
                // 避免过渡途中退场那份内容跟着外层状态提前跳变
                AnimatedContent(
                    targetState = isReorderMode,
                    transitionSpec = {
                        val forward = targetState
                        val offsetY = 40
                        if (forward) {
                            (fadeIn(tween(300, delayMillis = 100, easing = FastOutSlowInEasing))
                                    + slideInVertically(tween(300, delayMillis = 100, easing = FastOutSlowInEasing)) { offsetY })
                                .togetherWith(
                                    fadeOut(tween(200, easing = FastOutSlowInEasing))
                                            + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -offsetY }
                                )
                        } else {
                            (fadeIn(tween(300, delayMillis = 100, easing = FastOutSlowInEasing))
                                    + slideInVertically(tween(300, delayMillis = 100, easing = FastOutSlowInEasing)) { -offsetY })
                                .togetherWith(
                                    fadeOut(tween(200, easing = FastOutSlowInEasing))
                                            + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { offsetY }
                                )
                        }.using(SizeTransform(clip = false))
                    },
                    label = "playlist_reorder_transition"
                ) { reordering ->
                if (reordering) {
                    val statusBarHeight = melodiaStatusBarTopPadding()
                    val overlayHeight = 56.dp + statusBarHeight
                    val density = LocalDensity.current
                    val haptic = LocalHapticFeedback.current
                    val scope = rememberCoroutineScope()
                    val reorderListState = rememberLazyListState()

                    // 所有行结构一致，行高只随字体缩放整体变化，取任一已布局项的实测高度即可。
                    // 固定 60dp（44dp 封面 + 上下各 8dp）只作首帧兜底：字体放大后真实行高会超过它，
                    // 用估算值做位移补偿会让拖拽行每交换一次就相对手指漂移一点，长距离拖拽后明显脱手
                    val fallbackItemHeightPx = with(density) { 60.dp.toPx() }
                    var itemHeightPx by remember { mutableFloatStateOf(fallbackItemHeightPx) }
                    LaunchedEffect(reorderListState) {
                        snapshotFlow { reorderListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0 }
                            .collect { if (it > 0) itemHeightPx = it.toFloat() }
                    }

                    val topEdgeThreshold = with(density) { 80.dp.toPx() }
                    val bottomEdgeThreshold = with(density) { 100.dp.toPx() }
                    val bottomOverlayInsetPx = with(density) { LocalBottomOverlayInset.current.toPx() }

                    var draggedIndex by remember { mutableIntStateOf(-1) }
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    val isDraggingActive = draggedIndex >= 0

                    // 松手后把残留偏移动画收回 0 的落位态：直接归零会让拖拽行连同阴影一起硬切回槽位
                    var settlingIndex by remember { mutableIntStateOf(-1) }
                    val settleOffset = remember { Animatable(0f) }

                    // LazyColumn 只保留视口附近一小段 index 范围的组合状态，滚出这个范围的行会被直接销毁（连带杀死其
                    // pointerInput 手势协程）。swap 只根据 dragOffset/原始触摸位移推进 draggedIndex，本身并不关心列表
                    // 实际滚到了哪——必须拿这两个边界卡住，禁止把 draggedIndex 换到当前还没被组合出来的位置，否则拖拽
                    // 行迟早会被换到视口之外而断触。取不到布局信息时回退到列表自身边界，不能回退成 draggedIndex，
                    // 那会让上下两个方向同时被判定为越界而彻底钳死
                    fun maxComposedIndex(): Int =
                        reorderListState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: reorderedTracks.lastIndex
                    fun minComposedIndex(): Int =
                        reorderListState.layoutInfo.visibleItemsInfo.minOfOrNull { it.index } ?: 0

                    // 越界/待滚屏钳制：手指拖拽和自动滚屏两条路径都要走这里，避免各自实现不一致导致跳变。
                    // 不仅在首尾边界钳制，目标位置还没被组合出来（自动滚屏没跟上）时也要同样钳制——
                    // 否则 swap 被composed-range 挡住后 dragOffset 会无限增长，拖拽行会视觉上盖住好几行而不是贴着手指等滚屏赶上来。
                    // 回弹上限与下面的交换阈值同取 itemHeightPx：上限更小会在触边瞬间把 dragOffset 夹断产生可见回跳。
                    // 两处都直接读 itemHeightPx 而不另存 val，因为滚屏协程只在拖拽起止时重启，捕获的快照不会跟随实测行高更新
                    fun clampDragOffset(offset: Float): Float = when {
                        (draggedIndex <= 0 || draggedIndex <= minComposedIndex()) && offset < 0f -> max(offset, -itemHeightPx)
                        (draggedIndex >= reorderedTracks.lastIndex || draggedIndex >= maxComposedIndex()) && offset > 0f -> min(offset, itemHeightPx)
                        else -> offset
                    }

                    // 手势与自动滚屏共用的交换推进：dragOffset 每越过一整行就与相邻行交换并扣掉一行高，
                    // 使拖拽行的视觉位置在交换前后保持连续。阈值取整行高等价于拖拽行中心越过相邻行中心才交换，
                    // 与 ItemTouchHelper 判定一致；取半行高会让交换后的 dragOffset 正好落在反向交换的临界点上，
                    // 手指抖几像素就来回换
                    // LazyColumn 记的是视口首项的 key，重排后会让锚点跟着这个 key 跑到新 index，视口便整体
                    // 跳一行，把拖拽行顶出组合范围直接销毁掉，手势协程随之被杀而断触。
                    // 换位只是交换相邻两行内容，列表长度和行高都没变，滚动位置本就该原地不动，
                    // 因此换位涉及锚点时按原 index 重新钉一次（requestScrollToItem 只认 index、不再跟 key）
                    fun neutralizeAnchorShift(from: Int, to: Int) {
                        val anchor = reorderListState.firstVisibleItemIndex
                        if (from != anchor && to != anchor) return
                        reorderListState.requestScrollToItem(anchor, reorderListState.firstVisibleItemScrollOffset)
                    }

                    fun advanceSwaps() {
                        var swapped = false
                        while (dragOffset > itemHeightPx && draggedIndex < reorderedTracks.lastIndex && draggedIndex < maxComposedIndex()) {
                            neutralizeAnchorShift(draggedIndex, draggedIndex + 1)
                            Collections.swap(reorderedTracks, draggedIndex, draggedIndex + 1)
                            draggedIndex += 1
                            dragOffset = clampDragOffset(dragOffset - itemHeightPx)
                            swapped = true
                        }
                        while (dragOffset < -itemHeightPx && draggedIndex > 0 && draggedIndex > minComposedIndex()) {
                            neutralizeAnchorShift(draggedIndex, draggedIndex - 1)
                            Collections.swap(reorderedTracks, draggedIndex, draggedIndex - 1)
                            draggedIndex -= 1
                            dragOffset = clampDragOffset(dragOffset + itemHeightPx)
                            swapped = true
                        }
                        if (swapped) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }

                    // 拖拽到达屏幕边缘时的自动平滑滚屏协程（支持边缘停留渐进加速）。
                    // 按帧回调驱动而非 delay(16)：滚动速度以 px/秒 表达再乘真实帧间隔，
                    // 掉帧或非 60Hz 屏幕上速度和加速曲线都不会跟着变形
                    LaunchedEffect(isDraggingActive) {
                        if (!isDraggingActive) return@LaunchedEffect
                        var edgeDurationMs = 0f
                        var lastFrameNanos = 0L
                        while (isActive && draggedIndex >= 0) {
                            val frameNanos = withFrameNanos { it }
                            val deltaMs = if (lastFrameNanos == 0L) {
                                16f
                            } else {
                                ((frameNanos - lastFrameNanos) / 1_000_000f).coerceIn(1f, 64f)
                            }
                            lastFrameNanos = frameNanos

                            val layoutInfo = reorderListState.layoutInfo
                            val draggingItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIndex }
                            if (draggingItem == null) {
                                edgeDurationMs = 0f
                                continue
                            }

                            val currentTop = draggingItem.offset + dragOffset
                            val currentBottom = currentTop + draggingItem.size
                            val effectiveViewportEnd = layoutInfo.viewportEndOffset - bottomOverlayInsetPx

                            val isNearTop = currentTop < topEdgeThreshold && draggedIndex > 0
                            val isNearBottom = currentBottom > (effectiveViewportEnd - bottomEdgeThreshold) &&
                                draggedIndex < reorderedTracks.lastIndex
                            if (!isNearTop && !isNearBottom) {
                                edgeDurationMs = 0f
                                continue
                            }

                            edgeDurationMs += deltaMs
                            // 加速系数：边缘停留 1.5s 内从 1.0 渐进提升到 3.5 倍
                            val timeMultiplier = 1f + (edgeDurationMs / 600f).coerceAtMost(2.5f)
                            val depthRatio = if (isNearTop) {
                                ((topEdgeThreshold - currentTop) / topEdgeThreshold).coerceIn(0f, 1f)
                            } else {
                                ((currentBottom - (effectiveViewportEnd - bottomEdgeThreshold)) / bottomEdgeThreshold).coerceIn(0f, 1f)
                            }
                            val speedPxPerSec = (depthRatio * 1000f + 300f) * timeMultiplier
                            val stepPx = speedPxPerSec * deltaMs / 1000f

                            val scrolled = reorderListState.scrollBy(if (isNearTop) -stepPx else stepPx)
                            dragOffset = clampDragOffset(dragOffset + scrolled)
                            advanceSwaps()
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        PlaylistReorderTopBar(
                            overlayHeight = overlayHeight,
                            statusBarHeight = statusBarHeight,
                            isSaving = isSavingOrder,
                            onCancel = {
                                if (!isSavingOrder) {
                                    if (isOrderChanged) {
                                        showDiscardConfirmDialog = true
                                    } else {
                                        isReorderMode = false
                                    }
                                }
                            },
                            onDone = {
                                if (!isOrderChanged) {
                                    isReorderMode = false
                                } else {
                                    isSavingOrder = true
                                    // 提交的是此刻的快照，进入保存态后行的手势会被卸载（enabled=false），
                                    // 这里一并收掉可能残留的拖拽态，避免快照与用户后续动作不一致
                                    draggedIndex = -1
                                    dragOffset = 0f
                                    settlingIndex = -1
                                    viewModel.updateTrackOrder(state.playlist.id, reorderedTracks.toList()) { success ->
                                        isSavingOrder = false
                                        if (success) {
                                            isReorderMode = false
                                        }
                                    }
                                }
                            }
                        )

                        LazyColumn(
                            state = reorderListState,
                            // 拖拽时滚动完全交给自动滚屏协程接管，避免和列表自身的滚动手势抢占同一个 scroll mutex 导致断触
                            userScrollEnabled = !isDraggingActive,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = overlayHeight),
                            contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
                        ) {
                            itemsIndexed(
                                items = reorderedTracks,
                                key = { _, item -> item.id }
                            ) { idx, item ->
                                val isDragging = draggedIndex == idx
                                val isSettling = settlingIndex == idx
                                Box(
                                    // 抬起中的行自己负责位移，交给 animateItem 会和手动 translationY 打架
                                    modifier = if (isDragging || isSettling) Modifier.zIndex(1f) else Modifier.animateItem()
                                ) {
                                    DraggableSongRow(
                                        data = SongRowData(
                                            id = item.id,
                                            title = item.name,
                                            artist = item.ar.joinToString("/") { it.name },
                                            coverUrl = item.al.picUrl
                                        ),
                                        isCurrent = currentTrack?.mediaId == item.id.toString(),
                                        isPlaying = isPlaying,
                                        isPlayed = false,
                                        isDragging = isDragging,
                                        dragOffsetY = when {
                                            isDragging -> dragOffset
                                            isSettling -> settleOffset.value
                                            else -> 0f
                                        },
                                        enabled = !isSavingOrder,
                                        onClick = {},
                                        onDragStart = {
                                            // 同一时刻只允许一行处于拖拽态。多指分别长按两行时后者会覆盖 draggedIndex，
                                            // 而前一条手势协程仍然存活并继续推送位移，两条手势会同时驱动交换把列表搅乱
                                            if (draggedIndex < 0) {
                                                settlingIndex = -1
                                                draggedIndex = idx
                                                dragOffset = 0f
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        },
                                        onDrag = { delta ->
                                            // 交换后本行 idx 与 draggedIndex 同步变化，这个判等对拖拽行恒真，
                                            // 只会滤掉未被受理的那条并发手势
                                            if (draggedIndex == idx) {
                                                dragOffset = clampDragOffset(dragOffset + delta)
                                                advanceSwaps()
                                            }
                                        },
                                        onDragEnd = {
                                            if (draggedIndex == idx) {
                                                val releasedOffset = dragOffset
                                                draggedIndex = -1
                                                dragOffset = 0f
                                                if (releasedOffset != 0f) {
                                                    settlingIndex = idx
                                                    scope.launch {
                                                        settleOffset.snapTo(releasedOffset)
                                                        settleOffset.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                        )
                                                        settlingIndex = -1
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    PlaylistContent(
                        playlist       = state.playlist,
                        sortOrder = trackSort,
                        onCompleteTrackOrder = { viewModel.ensureAllTracksLoaded {} },
                        totalDurationMs = totalDurationMs,
                        showPlayCount = !isAlbum,
                        trackPlayCounts = state.trackPlayCounts,
                        canRemoveFromPlaylist = isOwnedPlaylist,
                        onRemoveFromPlaylist = { songId ->
                            viewModel.removeTrackFromPlaylist(state.playlist.id, songId)
                        },
                        pendingLikeIds = pendingLikeIds,
                        hasMoreTracks = state.hasMoreTracks,
                        isLoadingMoreTracks = state.isLoadingMoreTracks,
                        onLoadMoreTracks = { viewModel.loadMoreTracks() },
                        onLocateTrack = { trackId -> viewModel.ensureTrackLoaded(trackId) },
                        currentTrackId = currentTrack?.mediaId,
                    isPlaying      = isPlaying,
                    likedSongIds   = likedSongIds,
                    unplayableIds  = unplayableIds,
                    collectState   = collectState,
                    isLoggedIn     = userProfile != null,
                    recommendedSongs = state.recommendedSongs,
                    onBack         = onBack,
                    onArtistClick  = onArtistClick,
                    onAlbumClick   = onAlbumClick,
                    onToggleLike   = viewModel::toggleLikeSong,
                    onPlaySong     = { track ->
                        // 队列需包含歌单全部曲目，超过1000首时会先补全未加载的部分
                        viewModel.playTrackInPlaylist(track, { playbackSort.sort(it) }, requireComplete = { playbackSort.reversed })
                    },
                    onPlayRecommendedSong = viewModel::playRecommendedTrack,
                    onAddToPlayNext = { track ->
                        viewModel.addTrackToPlayNext(track)
                    },
                    onPlayAll = {
                        if (isThisOrderedQueue) viewModel.playerManager.togglePlayPause()
                        else viewModel.playAll(shuffle = isShuffleActive, orderTracks = { playbackSort.sort(it) },
                            toggleIfSameQueue = true, requireComplete = { playbackSort.reversed })
                    },
                    isShuffleActive = isShuffleActive,
                    isCurrentlyPlayingThis = isCurrentlyPlayingThis,
                    onShuffleToggle = {
                        // 纯开关，不直接触发播放；复用全局播放模式，跟全屏播放器的随机按钮保持一致
                        viewModel.playerManager.toggleShuffle()
                    },
                    onLikeClick = { songId ->
                        viewModel.prepareCollectDialog(songId)
                    },
                    onSaveCollection = { songId, items ->
                        viewModel.savePlaylistCollection(songId, items)
                    },
                    onSaveNewCollection = { name, songId ->
                        viewModel.createPlaylistAndAddSong(name, songId)
                    },
                    onRequireLogin = {
                        showLoginSheet = true
                    },
                    onRefreshRecommendations = {
                        viewModel.refreshRecommendations()
                    },
                    onAddRecommendSong = { track ->
                        viewModel.addRecommendSongToPlaylist(state.playlist.id, track)
                    },
                    isSubscribed = state.isSubscribed,
                    onSubscribeClick = {
                        if (userProfile == null) {
                            showLoginSheet = true
                        } else {
                            viewModel.toggleSubscribePlaylist()
                        }
                    },
                    onCommentsClick = {
                        showCommentsSheet = true
                        viewModel.loadPlaylistComments(playlistId)
                    },
                    onMoreClick = {
                        showMoreMenuSheet = true
                    },
                    isLikedSongsPlaylistView = isLikedSongsPlaylistView,
                    onShareClick = { sharePlaylistOrAlbum(state.playlist.name, state.playlist.id) },
                    onDownloadClick = { showDownloadQualitySheet = true },
                    onDownloadSongClick = { pendingDownloadTrack = it },
                    onHistoryClick = {},
                    historyDates = historyRecommendState.dates,
                    historySongsLoading = historyRecommendState.songsLoading,
                    showHistoryDatePicker = true,
                    selectedHistoryDate = selectedHistoryDate,
                    onSelectedHistoryDateChange = { selectedHistoryDate = it },
                    onLoadHistoryDetail = { viewModel.loadHistoryDetail(it) },
                    onLoadDailyRecommend = { viewModel.loadPlaylist(-1L) },
                    onCreatorClick = onNavigateToProfile,
                    selectionUi = TrackSelectionUi(
                        active = selection.active,
                        selectedIds = selection.selectedIds,
                        onEnter = viewModel::enterSelection,
                        onToggle = viewModel::toggleSelected,
                        busy = batchProgress.active,
                        progressText = batchProgress.text.takeIf { batchProgress.active }
                    ),
                    selectionActions = playlistSelectionActions(
                        canRemove = isOwnedPlaylist,
                        playNext = { viewModel.batchPlayNext() },
                        addToPlaylist = {
                            if (userProfile == null) showLoginSheet = true
                            else { viewModel.prepareImportTargets(state.playlist.id); showBatchTargetSheet = true }
                        },
                        like = { if (userProfile == null) showLoginSheet = true else viewModel.batchLike() },
                        unlike = { if (userProfile == null) showLoginSheet = true else showBatchUnlikeConfirm = true },
                        download = { showBatchDownloadSheet = true },
                        remove = { showBatchRemoveConfirm = true }
                    ),
                    onToggleSelectAll = viewModel::toggleSelectAll,
                    onExitSelection = viewModel::exitSelection
                )
            }
            }
        }
    }

    if (showLoginSheet) {
            LoginBottomSheet(
                onDismiss = { showLoginSheet = false },
                onWebLogin = {
                    showLoginSheet = false
                    showWebViewLogin = true
                },
                onLoginSuccess = { cookies ->
                    showLoginSheet = false
                    viewModel.handleLoginSuccess(cookies)
                }
            )
        }

        AnimatedVisibility(
            visible = showWebViewLogin,
            enter = slideInVertically(tween(ScreenSlideDurationMs)) { it } + fadeIn(tween(ScreenSlideDurationMs)),
            exit = slideOutVertically(tween(ScreenSlideDurationMs)) { it } + fadeOut(tween(ScreenSlideDurationMs))
        ) {
            WebViewLoginScreen(
                onClose = { showWebViewLogin = false },
                onLoginSuccess = { cookies ->
                    showWebViewLogin = false
                    viewModel.handleLoginSuccess(cookies)
                }
            )
        }

        AnimatedVisibility(
            visible = showCommentsSheet,
            enter = slideInVertically(tween(ScreenSlideDurationMs)) { it } + fadeIn(tween(ScreenSlideDurationMs)),
            exit = slideOutVertically(tween(ScreenSlideDurationMs)) { it } + fadeOut(tween(ScreenSlideDurationMs))
        ) {
            CommentFullScreen(
                commentsState = commentsState,
                currentUserId = userProfile?.uid,
                composerState = composerState,
                onBack = { showCommentsSheet = false },
                onSortChange = viewModel::changeCommentSort,
                onLikeComment = viewModel::likeComment,
                onUserClick = onNavigateToProfile,
                onExpandFloor = { comment ->
                    showCommentFloor = true
                    viewModel.openCommentFloor(comment)
                },
                onDeleteClick = { comment -> viewModel.deleteCommentItem(comment) },
                onSubmitComment = { content, target ->
                    if (target != null) {
                        viewModel.submitCommentReply(target.commentId, content)
                    } else {
                        viewModel.submitComment(content)
                    }
                },
                onRequireLogin = { showLoginSheet = true },
                onLoadMore = viewModel::loadMoreComments,
                onRetry = { viewModel.loadPlaylistComments(playlistId) },
                bottomOverlayInset = LocalBottomOverlayInset.current
            )
        }

        AnimatedVisibility(
            visible = showCommentFloor,
            enter = slideInVertically(tween(ScreenSlideDurationMs)) { it } + fadeIn(tween(ScreenSlideDurationMs)),
            exit = slideOutVertically(tween(ScreenSlideDurationMs)) { it } + fadeOut(tween(ScreenSlideDurationMs))
        ) {
            CommentFloorScreen(
                floorState = floorState,
                currentUserId = userProfile?.uid,
                composerState = composerState,
                onBack = { showCommentFloor = false },
                onLoadMore = viewModel::loadMoreCommentFloor,
                onSubmitReply = { parentCommentId, content ->
                    viewModel.submitCommentReply(parentCommentId, content)
                },
                onRequireLogin = { showLoginSheet = true },
                onDeleteClick = { comment -> viewModel.deleteCommentItem(comment) },
                onLikeClick = { comment -> viewModel.likeComment(comment) },
                onRetry = { (floorState as? CommentFloorState.Success)?.ownerComment?.let(viewModel::openCommentFloor) },
                onUserClick = onNavigateToProfile
            )
        }



        val successState = uiState as? PlaylistUiState.Success
        if (showDownloadQualitySheet && successState != null) {
            val playlist = successState.playlist
            // 超过1000首的歌单 tracks 被截断，以完整的 trackIds 统计已下载数
            val batchSongIds = remember(playlist.trackIds, playlist.tracks) {
                playlist.trackIds.map { it.id }.ifEmpty { playlist.tracks.map { it.id } }
            }
            com.lin0721.linmusic.core.download.ui.DownloadQualityPickerSheet(
                onQualitySelected = { level ->
                    viewModel.downloadPlaylist(playlist.id, playlist.name, level)
                    showDownloadQualitySheet = false
                },
                onDismiss = { showDownloadQualitySheet = false },
                headline = playlist.name,
                supportingText = "共 ${batchSongIds.size} 首",
                batchSongIds = batchSongIds
            )
        }

        if (showBatchTargetSheet) {
            PlaylistImportTargetSheet(
                importState = importState,
                onDismiss = { showBatchTargetSheet = false },
                onSelectTarget = { targetId ->
                    viewModel.batchAddToPlaylist(targetId)
                    showBatchTargetSheet = false
                },
                onCreateAndImport = { name ->
                    viewModel.batchCreatePlaylistAndAdd(name)
                    showBatchTargetSheet = false
                }
            )
        }

        if (showBatchDownloadSheet) {
            com.lin0721.linmusic.core.download.ui.DownloadQualityPickerSheet(
                onQualitySelected = { level ->
                    viewModel.batchDownload(level)
                    showBatchDownloadSheet = false
                },
                onDismiss = { showBatchDownloadSheet = false },
                headline = "批量下载",
                supportingText = "共 ${selection.count} 首",
                batchSongIds = selection.selectedIds.toList()
            )
        }

        if (showBatchUnlikeConfirm) AlertDialog(
            onDismissRequest = { showBatchUnlikeConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("取消喜欢", fontWeight = FontWeight.Bold) },
            text = { Text("取消选中歌曲的喜欢标记？歌曲会从我喜欢的音乐中移除，其他普通歌单保留这些歌曲。") },
            confirmButton = { MelodiaTextButton(onClick = { showBatchUnlikeConfirm = false; viewModel.batchLike(false) }) { Text("取消喜欢") } },
            dismissButton = { MelodiaTextButton(onClick = { showBatchUnlikeConfirm = false }) { Text("返回") } }
        )

        if (showBatchRemoveConfirm) {
            AlertDialog(
                onDismissRequest = { showBatchRemoveConfirm = false },
                title = {
                    Text(
                        text = "从歌单中移出",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "确定要从当前歌单移出选中的 ${selection.count} 首歌曲吗？",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    MelodiaTextButton(
                        onClick = {
                            showBatchRemoveConfirm = false
                            viewModel.batchRemove()
                        }
                    ) { Text("移出歌单", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    MelodiaTextButton(onClick = { showBatchRemoveConfirm = false }) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (pendingDownloadTrack != null) {
            val trackToDownload = pendingDownloadTrack!!
            com.lin0721.linmusic.core.download.ui.DownloadQualityPickerSheet(
                onQualitySelected = { level ->
                    viewModel.downloadTrack(trackToDownload, level)
                    pendingDownloadTrack = null
                },
                onDismiss = { pendingDownloadTrack = null },
                songId = trackToDownload.id,
                maxDownloadLevel = trackToDownload.privilege?.dlLevel,
                headline = trackToDownload.name,
                supportingText = trackToDownload.ar.joinToString("/") { it.name }
            )
        }

        if (artistPickerEntries.isNotEmpty()) {
            ArtistPickerSheet(
                artists = artistPickerEntries,
                onArtistClick = onArtistClick,
                onDismiss = { artistPickerEntries = emptyList() }
            )
        }

        albumArtistCorrection?.let { album ->
            AlbumArtistCorrectionHost(album, onDismiss = { albumArtistCorrection = null })
        }
        if (showSortSheet && successState != null) {
            PlaylistSortSheet(
                sortOrder = trackSort,
                onSortOrderChange = { requested ->
                    activeSortScope?.let { sortViewModel.save(it, requested) }
                    showSortSheet = false
                },
                onDismiss = { showSortSheet = false }
            )
        }
        if (showMoreMenuSheet && successState != null) {
            val playlist = successState.playlist
            val isLikedSongsPlaylistView = userProfile?.let { profile ->
                playlist.isOwnLikedSongs(profile.uid)
            } == true
            val moreMenuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showMoreMenuSheet = false },
                sheetState = moreMenuSheetState,
                containerColor = MaterialTheme.colorScheme.background,
                shape = BottomSheetShape,
                dragHandle = { MelodiaDragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = MelodiaSpacing.md)
                ) {
                    // 优先用专辑署名歌手，旧数据/歌单退回首曲歌手；多位时全部保留供选择
                    val linkedArtists = playlist.artists
                        .ifEmpty { playlist.tracks.firstOrNull()?.ar.orEmpty() }
                        .filter { it.id > 0L }
                        .distinctBy { it.id }
                    val artistName = linkedArtists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }
                    val resourceLabel = if (isAlbum) "专辑" else "歌单"

                    val subtitleText = buildString {
                        val author = if (isAlbum) artistName else playlist.creator?.nickname
                        if (!author.isNullOrBlank()) {
                            append(author)
                        }
                        val totalTrackCount = if (playlist.trackIds.isNotEmpty()) playlist.trackIds.size else playlist.tracks.size
                        if (totalTrackCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${totalTrackCount}首")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SubcomposeAsyncImage(
                            model = if (playlist.coverImgUrl.isNotEmpty()) "${playlist.coverImgUrl}?param=150y150" else null,
                            contentDescription = playlist.name,
                            contentScale = ContentScale.Crop,
                            loading = { CoverPlaceholder() },
                            error = { CoverPlaceholder() },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitleText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
                                Text(
                                    text = subtitleText,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = MelodiaSpacing.sm)
                    )

                    val isManageable = !isAlbum && userProfile != null &&
                        playlist.creator?.userId == userProfile?.uid &&
                        playlist.id > 0L &&
                        !isLikedSongsPlaylistView

                    val menuItems = buildList {
                        if (!isAlbum && playlist.id > 0L) addAll(playlistEditingMenuItems(
                            isManageable = isManageable,
                            isTracksEmpty = playlist.tracks.isEmpty(),
                            sortOrder = trackSort,
                            onEditSongsClick = {
                                showMoreMenuSheet = false
                                viewModel.enterSelection()
                            },
                            onAddMusicClick = {
                                showMoreMenuSheet = false
                                showAddMusicSheet = true
                            },
                            onEditOrderClick = {
                                showMoreMenuSheet = false
                                // Saving order replaces the complete server list; load every track first.
                                viewModel.ensureAllTracksLoaded { allTracks ->
                                    reorderedTracks.clear()
                                    reorderedTracks.addAll(allTracks)
                                    isReorderMode = true
                                }
                            },
                            onSortClick = {
                                showMoreMenuSheet = false
                                showSortSheet = true
                            },
                            onEditInfoClick = {
                                showMoreMenuSheet = false
                                showEditInfoDialog = true
                            },
                            onSortOrderChange = { requested ->
                                activeSortScope?.let { sortViewModel.save(it, requested) }
                            }
                        ))
                        if (isAlbum) add(PlaylistMenuItem(
                            icon = Icons.Default.LibraryAdd,
                            title = "显示在歌手专辑页"
                        ) {
                            showMoreMenuSheet = false
                            albumArtistCorrection = playlist
                        })
                        if (isAlbum) add(PlaylistMenuItem(
                            icon = Icons.Default.Link,
                            title = "关联本地专辑（专辑内匹配）"
                        ) {
                            showMoreMenuSheet = false
                            albumAssociation?.openOnline(Album(id = playlist.id, name = playlist.name,
                                picUrl = playlist.coverImgUrl, artists = playlist.artists.ifEmpty {
                                    playlist.tracks.firstOrNull()?.ar.orEmpty()
                                }), playlist.tracks)
                        })
                        if (!isLikedSongsPlaylistView) add(
                            PlaylistMenuItem(
                                icon = if (successState.isSubscribed) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                                title = if (successState.isSubscribed) "取消收藏$resourceLabel" else "收藏$resourceLabel"
                            ) {
                                showMoreMenuSheet = false
                                if (userProfile == null) {
                                    showLoginSheet = true
                                } else {
                                    viewModel.toggleSubscribePlaylist()
                                }
                            }
                        )
                        add(
                            PlaylistMenuItem(
                                icon = Icons.Default.Person,
                                title = "跳转至艺人"
                            ) {
                                showMoreMenuSheet = false
                                when (linkedArtists.size) {
                                    0 -> com.lin0721.linmusic.core.ui.components.ToastManager.showToast("未找到关联艺人信息")
                                    1 -> onArtistClick(linkedArtists.first().id)
                                    else -> artistPickerEntries = linkedArtists.map { artist ->
                                        ArtistPickerEntry(
                                            id = artist.id,
                                            name = artist.name,
                                            avatarUrl = artist.picUrl.ifBlank { artist.img1v1Url }.takeIf { it.isNotBlank() }
                                        )
                                    }
                                }
                            }
                        )
                        add(
                            PlaylistMenuItem(
                                icon = Icons.AutoMirrored.Filled.QueueMusic,
                                title = "加入播放队列"
                            ) {
                                showMoreMenuSheet = false
                                viewModel.addAllTracksToPlayNext()
                            }
                        )
                        add(
                            PlaylistMenuItem(
                                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                                title = "添加到歌单"
                            ) {
                                showMoreMenuSheet = false
                                if (userProfile == null) {
                                    showLoginSheet = true
                                } else {
                                    viewModel.prepareImportTargets(playlist.id)
                                    showImportTargetSheet = true
                                }
                            }
                        )
                        add(
                            PlaylistMenuItem(
                                icon = Icons.Default.Share,
                                title = "分享$resourceLabel"
                            ) {
                                showMoreMenuSheet = false
                                sharePlaylistOrAlbum(playlist.name, playlist.id)
                            }
                        )
                        add(
                            PlaylistMenuItem(
                                icon = Icons.Default.Download,
                                title = "下载$resourceLabel"
                            ) {
                                showMoreMenuSheet = false
                                showDownloadQualitySheet = true
                            }
                        )
                        if (isManageable) {
                            add(
                                PlaylistMenuItem(
                                    icon = Icons.Default.Delete,
                                    title = "删除歌单",
                                    isDestructive = true
                                ) {
                                    showMoreMenuSheet = false
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(menuItems, key = { it.title }) { item ->
                            PlaylistMenuRow(item)
                        }
                    }
                }
            }
        }

        if (showImportTargetSheet) {
            PlaylistImportTargetSheet(
                importState = importState,
                onDismiss = { showImportTargetSheet = false },
                onSelectTarget = { targetId ->
                    viewModel.importAllTracksTo(targetId)
                    showImportTargetSheet = false
                },
                onCreateAndImport = { name ->
                    viewModel.createPlaylistAndImportAll(name)
                    showImportTargetSheet = false
                }
            )
        }

        if (showAddMusicSheet && successState != null) {
            val playlist = successState.playlist
            AddMusicToPlaylistSheet(
                searchQuery = addMusicSearchQuery,
                searchState = addMusicSearchState,
                existingTrackIds = remember(playlist.tracks) {
                    playlist.tracks.map { it.id }.toSet()
                },
                recommendedSongs = successState.recommendedSongs,
                onQueryChange = viewModel::updateAddMusicSearchQuery,
                onClearQuery = viewModel::clearAddMusicSearch,
                onAddTrack = { track, onComplete ->
                    viewModel.addTrackToPlaylist(playlist.id, track) { onComplete() }
                },
                onDismiss = {
                    showAddMusicSheet = false
                    viewModel.clearAddMusicSearch()
                }
            )
        }

        if (showEditInfoDialog && successState != null) {
            val playlist = successState.playlist
            EditPlaylistInfoDialog(
                initialName = playlist.name,
                initialDescription = playlist.description.orEmpty(),
                coverImgUrl = playlist.coverImgUrl,
                pendingCoverUri = pendingCoverUri,
                isSaving = isSavingInfo,
                onEditCoverClick = launchCoverCropper,
                onDismiss = {
                    showEditInfoDialog = false
                    pendingCoverUri = null
                },
                onConfirm = { newName, newDesc ->
                    coroutineScope.launch {
                        val stagedUri = pendingCoverUri
                        val coverBytes = stagedUri?.let { uri ->
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            }
                        }
                        if (stagedUri != null && coverBytes == null) {
                            com.lin0721.linmusic.core.ui.components.ToastManager.showToast("读取裁剪后的图片失败")
                            return@launch
                        }
                        viewModel.updatePlaylistInfo(
                            id = playlist.id,
                            originalName = playlist.name,
                            newName = newName,
                            originalDesc = playlist.description,
                            newDesc = newDesc,
                            coverBytes = coverBytes
                        ) { success ->
                            if (success) {
                                showEditInfoDialog = false
                                pendingCoverUri = null
                            }
                        }
                    }
                }
            )
        }

        if (showDeleteConfirmDialog && successState != null) {
            val playlist = successState.playlist
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = {
                    Text(
                        text = "删除歌单",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "确定要删除歌单「${playlist.name}」吗？删除后将无法恢复。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    MelodiaTextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deletePlaylist(playlist.id) {
                                onBack()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("删除", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    MelodiaTextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            )
        }

        if (showDiscardConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirmDialog = false },
                title = {
                    Text(
                        text = "放弃修改？",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "当前调整的歌曲顺序尚未保存，确定要放弃修改吗？",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    MelodiaTextButton(
                        onClick = {
                            showDiscardConfirmDialog = false
                            isReorderMode = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("放弃", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    MelodiaTextButton(onClick = { showDiscardConfirmDialog = false }) {
                        Text("继续调整", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            )
        }

    }
}
