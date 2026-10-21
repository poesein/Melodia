package com.lin0721.linmusic.feature.library.textimport

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** Keep upward edge momentum out of an already expanded sheet's settle animation.
 * Downward gestures still reach the sheet, and ordinary list flings remain untouched.
 */
internal class PlaylistTextImportScrollBoundary(
    private val isExpanded: () -> Boolean,
    private val canScrollForward: () -> Boolean
) : NestedScrollConnection {
    override suspend fun onPreFling(available: Velocity): Velocity =
        if (isExpanded() && !canScrollForward() && available.y < 0f) Velocity(0f, available.y)
        else Velocity.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (isExpanded() && available.y < 0f) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (isExpanded() && available.y < 0f) Velocity(0f, available.y) else Velocity.Zero
}
