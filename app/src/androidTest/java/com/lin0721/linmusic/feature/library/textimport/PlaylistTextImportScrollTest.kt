package com.lin0721.linmusic.feature.library.textimport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class PlaylistTextImportScrollTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var sheet: SheetState
    private lateinit var list: LazyListState
    private var dismissed = false
    private var recording = false
    private val offsets = mutableListOf<Float>()

    private fun showSheet() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                list = rememberLazyListState()
                val boundary = remember(sheet, list) {
                    PlaylistTextImportScrollBoundary(
                        { sheet.currentValue == SheetValue.Expanded && sheet.targetValue == SheetValue.Expanded },
                        { list.canScrollForward })
                }
                LaunchedEffect(sheet) {
                    while (true) {
                        withFrameNanos { }
                        if (recording && sheet.currentValue == SheetValue.Expanded) offsets.add(sheet.requireOffset())
                    }
                }
                ModalBottomSheet(onDismissRequest = { dismissed = true }, sheetState = sheet) {
                    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
                        Text("导入匹配结果", Modifier.height(60.dp))
                        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()
                            .nestedScroll(boundary).testTag("results")) {
                            items(200) { Text("歌曲 $it", Modifier.fillMaxWidth().height(72.dp)) }
                        }
                        Text("选择歌单并导入", Modifier.height(60.dp))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(SheetValue.Expanded, sheet.currentValue) }
    }

    @Test fun repeatedUpwardSwipesAtBottomLeaveSheetStable() {
        showSheet()
        compose.onNodeWithTag("results").performScrollToIndex(199)
        compose.mainClock.advanceTimeBy(100)
        val expandedOffset = sheet.requireOffset()
        repeat(8) {
            compose.onNodeWithTag("results").performTouchInput { swipeUp(durationMillis = 120) }
            compose.mainClock.advanceTimeBy(600)
        }
        compose.runOnIdle { recording = true }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle {
            assertFalse(list.canScrollForward)
            assertFalse(dismissed)
            assertTrue(offsets.size >= 60)
            assertTrue("Sheet kept oscillating after the gesture", offsets.max() - offsets.min() < 1f)
            assertEquals(expandedOffset, sheet.requireOffset(), 1f)
        }
    }

    @Test fun upwardFlingStillScrollsTheList() {
        showSheet()
        compose.onNodeWithTag("results").performTouchInput { swipeUp(durationMillis = 120) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertTrue(list.firstVisibleItemIndex > 0)
            assertTrue(list.canScrollForward)
            assertFalse(dismissed)
            assertEquals(SheetValue.Expanded, sheet.currentValue)
        }
    }

    @Test fun downwardSwipeAtTopStillDismissesTheSheet() {
        showSheet()
        compose.onNodeWithTag("results").performTouchInput { swipeDown(durationMillis = 120) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
