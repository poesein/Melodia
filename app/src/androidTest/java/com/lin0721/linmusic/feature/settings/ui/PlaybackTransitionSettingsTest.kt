package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaybackTransitionSettingsTest {
    @get:Rule val compose = createComposeRule()
    private var duration = 200
    private fun show() {
        compose.setContent {
            var smooth by remember { mutableStateOf(false) }
            var ms by remember { mutableIntStateOf(200) }
            var trim by remember { mutableStateOf(true) }
            MaterialTheme {
                Column {
                    PlaybackTransitionSettings(smooth, ms, trim, { smooth = it }, { duration = it; ms = it }, { trim = it })
                }
            }
        }
    }
    @Test fun modeSelectionShowsOverlapControlsOnlyInSmoothMode() {
        show()
        compose.onNodeWithText("重叠时间").assertDoesNotExist()
        compose.onNodeWithText("平滑衔接").performClick()
        compose.onNodeWithText("重叠时间").assertExists()
        compose.onNodeWithText("缩短首尾静音").assertExists()
        compose.onNodeWithText("原始无缝").performClick()
        compose.onNodeWithText("重叠时间").assertDoesNotExist()
    }
    @Test fun overlapCanBeEnteredPrecisely() {
        show()
        compose.onNodeWithText("平滑衔接").performClick()
        compose.onAllNodesWithText("0.20 秒")[0].performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("0.35")
        compose.onNodeWithText("应用").performClick()
        compose.runOnIdle { assertEquals(350, duration) }
        compose.onNodeWithText("0.35 秒").assertExists()
    }
    @Test fun invalidOverlapCannotReplaceSavedValue() {
        show()
        compose.onNodeWithText("平滑衔接").performClick()
        compose.onAllNodesWithText("0.20 秒")[0].performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText("应用").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(200, duration) }
    }
}
