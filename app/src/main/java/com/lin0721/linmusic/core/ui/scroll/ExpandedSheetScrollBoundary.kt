package com.lin0721.linmusic.core.ui.scroll

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** Upward edge momentum must not restart an already expanded sheet's settle animation.
 * Leave list flings, horizontal scrolling and downward sheet dismissal unchanged.
 */
internal class ExpandedSheetScrollBoundary(
    private val isExpanded: () -> Boolean,
    private val canScrollForward: () -> Boolean
) : NestedScrollConnection {
    override suspend fun onPreFling(available: Velocity): Velocity =
        if (isExpanded() && !canScrollForward() && available.y < 0f) Velocity(0f, available.y) else Velocity.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (isExpanded() && available.y < 0f) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (isExpanded() && available.y < 0f) Velocity(0f, available.y) else Velocity.Zero
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberExpandedSheetScrollBoundary(sheet: SheetState, canScrollForward: () -> Boolean): NestedScrollConnection {
    val latestCanScroll by rememberUpdatedState(canScrollForward)
    return remember(sheet) {
        ExpandedSheetScrollBoundary(
            { sheet.currentValue == SheetValue.Expanded && sheet.targetValue == SheetValue.Expanded },
            { latestCanScroll() }
        )
    }
}
