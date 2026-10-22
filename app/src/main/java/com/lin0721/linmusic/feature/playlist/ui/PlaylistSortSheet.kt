package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lin0721.linmusic.core.ui.scroll.rememberExpandedSheetScrollBoundary
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// ────────────────────────────────────────────────────────────────────────────
// 歌单内歌曲排序方式弹层，复用与"更多操作""收藏到歌单"一致的弹层视觉规范
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSortSheet(
    sortOrder: PlaylistTrackSort,
    onSortOrderChange: (PlaylistTrackSort) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val boundary = rememberExpandedSheetScrollBoundary(sheetState) { scrollState.canScrollForward }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(boundary)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Text(
                text = "排序方式",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            PlaylistSortOption.entries.forEach { option ->
                val selected = option == sortOrder.option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortOrderChange(sortOrder.copy(option = option)) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (option == PlaylistSortOption.DEFAULT) "默认顺序" else option.label,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            Row(Modifier.fillMaxWidth().clickable { onSortOrderChange(sortOrder.copy(reversed = !sortOrder.reversed)) }
                .padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("倒序", fontSize = 15.sp)
                    Text("反转当前排列；长歌单会先加载完整歌曲", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = sortOrder.reversed, onCheckedChange = { onSortOrderChange(sortOrder.copy(reversed = it)) },
                    modifier = Modifier.semantics { contentDescription = "倒序排列歌曲" })
            }
        }
    }
}
