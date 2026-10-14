package com.lin0721.linmusic.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.AdaptiveContentWidth
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.DiscoverySectionSkeleton
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.search.domain.PlaylistTag

private val tagFallbackColors = listOf(
    Color(0xFF1E3264),
    Color(0xFF8D67AB),
    Color(0xFF148A08),
    Color(0xFFE13300),
    Color(0xFF477D95),
    Color(0xFFE8115B),
    Color(0xFF537AA1),
    Color(0xFF7D4B32),
    Color(0xFF1A6B52),
    Color(0xFFE91429),
)

private val TagSmallHeight = 72.dp

@Composable
internal fun SearchDiscoveryContent(
    state: DiscoveryUiState,
    onPlaylistTagClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        DiscoveryUiState.Loading -> {
            Column(modifier = Modifier.fillMaxSize().padding(top = MelodiaSpacing.md)) {
                DiscoverySectionSkeleton()
            }
        }
        is DiscoveryUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(message = state.message, onRetry = onRetry)
            }
        }
        is DiscoveryUiState.Success -> {
            val tagGroups = state.playlistTags.chunked(3)
            AdaptiveContentWidth {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = MelodiaSpacing.md,
                        end = MelodiaSpacing.md,
                        top = MelodiaSpacing.sm,
                        bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (tagGroups.isNotEmpty()) {
                        item(key = "tags_header") { SectionHeader("精品歌单") }
                        itemsIndexed(tagGroups, key = { index, _ -> "tag_group_$index" }) { groupIndex, group ->
                            PlaylistTagGroup(
                                tags = group,
                                groupIndex = groupIndex,
                                onClick = onPlaylistTagClick,
                                modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = MelodiaSpacing.lg, bottom = MelodiaSpacing.sm)
    )
}

// 每组三张：一大两小，大卡左右交替摆放；末组不足三张时均分一行
@Composable
private fun PlaylistTagGroup(
    tags: List<PlaylistTag>,
    groupIndex: Int,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MelodiaSpacing.sm
    val bigHeight: Dp = TagSmallHeight * 2 + spacing
    val colorOffset = groupIndex * 3

    if (tags.size < 3) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            tags.forEachIndexed { index, tag ->
                PlaylistTagCard(
                    tag = tag,
                    fallbackColor = tagFallbackColors[(colorOffset + index) % tagFallbackColors.size],
                    onClick = { onClick(tag.name) },
                    modifier = Modifier.weight(1f).height(TagSmallHeight + 24.dp)
                )
            }
        }
        return
    }

    val bigOnLeft = groupIndex % 2 == 0
    Row(
        modifier = modifier.fillMaxWidth().height(bigHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        val big = @Composable { m: Modifier ->
            PlaylistTagCard(
                tag = tags[0],
                fallbackColor = tagFallbackColors[colorOffset % tagFallbackColors.size],
                onClick = { onClick(tags[0].name) },
                large = true,
                modifier = m
            )
        }
        val smalls = @Composable { m: Modifier ->
            Column(modifier = m, verticalArrangement = Arrangement.spacedBy(spacing)) {
                for (i in 1..2) {
                    PlaylistTagCard(
                        tag = tags[i],
                        fallbackColor = tagFallbackColors[(colorOffset + i) % tagFallbackColors.size],
                        onClick = { onClick(tags[i].name) },
                        modifier = Modifier.fillMaxWidth().height(TagSmallHeight)
                    )
                }
            }
        }
        if (bigOnLeft) {
            big(Modifier.weight(1.3f).fillMaxHeight())
            smalls(Modifier.weight(1f).fillMaxHeight())
        } else {
            smalls(Modifier.weight(1f).fillMaxHeight())
            big(Modifier.weight(1.3f).fillMaxHeight())
        }
    }
}

@Composable
private fun PlaylistTagCard(
    tag: PlaylistTag,
    fallbackColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    Box(
        modifier = modifier
            .pressable(MelodiaPress.Card, onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(if (tag.coverUrl.isBlank()) fallbackColor else MaterialTheme.colorScheme.surface)
    ) {
        if (tag.coverUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = tag.coverUrl,
                contentDescription = tag.name,
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
            )
        }
        Text(
            text = tag.name,
            color = Color.White,
            fontSize = if (large) 20.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )
    }
}
