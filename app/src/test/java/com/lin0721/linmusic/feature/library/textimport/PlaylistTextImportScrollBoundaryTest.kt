package com.lin0721.linmusic.feature.library.textimport

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTextImportScrollBoundaryTest {
    @Test fun upwardFlingAtBottomIsConsumedOnlyOnVerticalAxis() = runTest {
        val boundary = PlaylistTextImportScrollBoundary({ true }, { false })
        assertEquals(Velocity(0f, -900f), boundary.onPreFling(Velocity(12f, -900f)))
    }

    @Test fun normalListFlingIsNotInterceptedBeforeItReachesBottom() = runTest {
        val boundary = PlaylistTextImportScrollBoundary({ true }, { true })
        assertEquals(Velocity.Zero, boundary.onPreFling(Velocity(0f, -900f)))
        assertEquals(Velocity(0f, -200f), boundary.onPostFling(Velocity(0f, -700f), Velocity(0f, -200f)))
    }

    @Test fun repeatedUpwardDragRemainderCannotDriveSheet() {
        val boundary = PlaylistTextImportScrollBoundary({ true }, { false })
        repeat(20) {
            assertEquals(Offset(0f, -8f), boundary.onPostScroll(Offset.Zero, Offset(4f, -8f), NestedScrollSource.UserInput))
        }
    }

    @Test fun downwardDismissalAndHorizontalRemaindersRemainAvailable() = runTest {
        val boundary = PlaylistTextImportScrollBoundary({ true }, { false })
        assertEquals(Velocity.Zero, boundary.onPreFling(Velocity(0f, 900f)))
        assertEquals(Velocity.Zero, boundary.onPostFling(Velocity.Zero, Velocity(0f, 900f)))
        assertEquals(Offset.Zero, boundary.onPostScroll(Offset.Zero, Offset(4f, 8f), NestedScrollSource.UserInput))
        assertEquals(Offset.Zero, boundary.onPostScroll(Offset.Zero, Offset(4f, 0f), NestedScrollSource.UserInput))
    }

    @Test fun sheetOpeningAndExpansionAreNotBlocked() = runTest {
        val boundary = PlaylistTextImportScrollBoundary({ false }, { false })
        assertEquals(Velocity.Zero, boundary.onPreFling(Velocity(0f, -900f)))
        assertEquals(Velocity.Zero, boundary.onPostFling(Velocity.Zero, Velocity(0f, -900f)))
        assertEquals(Offset.Zero, boundary.onPostScroll(Offset.Zero, Offset(0f, -8f), NestedScrollSource.UserInput))
    }
}
