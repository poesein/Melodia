package com.lin0721.linmusic.feature.home.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.FilterPillInactive
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PressStyle
import com.lin0721.linmusic.core.ui.theme.PillRadius

// Homepage categories retain their original IDs for saved navigation compatibility.
@Composable
fun HomeSharedHeader(
    userProfile: UserProfile?,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAvatarClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(2f)
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MelodiaSpacing.sm, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.padding(start = 20.dp, end = 12.dp)) {
                if (userProfile != null) {
                    AsyncImage(
                        model = "${userProfile.avatarUrl}?param=200y200",
                        contentDescription = "用户头像",
                        modifier = Modifier
                            .size(36.dp)
                            .pressable(MelodiaPress.Icon) { onAvatarClick() }
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { onAvatarClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "未登录",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            FilterPills(
                selectedIndex = selectedTab,
                onSelected = onTabSelected,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(end = 20.dp)
            )
        }
    }
}

private val PillShapeFull = RoundedCornerShape(PillRadius)

// Music and latest now belong to their independent bottom-bar root.
@Composable
fun FilterPills(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp)
) {
    val labels = listOf(TAB_ALL to "全部", TAB_PODCAST to "播客")

    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { position, (index, label) ->
            item(key = label) {
                FilterPillChip(
                    text = label,
                    selected = index == selectedIndex,
                    onClick = { onSelected(index) },
                    modifier = Modifier
                        .padding(start = if (position == 0) 0.dp else 12.dp)
                )
            }
        }
    }
}

// 单个筛选药丸，选中态背景色/文字色带过渡动画
@Composable
internal fun FilterPillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape = PillShapeFull,
    pressStyle: PressStyle = MelodiaPress.Pill,
    animateColors: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = FilterPillInactive,
    labelStartInset: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val targetBackground = if (selected) activeColor else inactiveColor
    val targetContent = if (selected) MaterialTheme.colorScheme.onPrimary else Color.LightGray
    // 动画值无条件求值，避免 animateColors 变化时组合槽位对不上
    val animatedBackground by animateColorAsState(targetValue = targetBackground, label = "pillBackground")
    val animatedContent by animateColorAsState(targetValue = targetContent, label = "pillContent")
    val backgroundColor = if (animateColors) animatedBackground else targetBackground
    val contentColor = if (animateColors) animatedContent else targetContent
    Box(
        modifier = modifier
            .height(36.dp)
            .pressable(pressStyle, onClick = onClick)
            .clip(shape)
            .background(backgroundColor)
            .padding(start = 18.dp + labelStartInset, end = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = contentColor, fontSize = 14.sp)
    }
}
