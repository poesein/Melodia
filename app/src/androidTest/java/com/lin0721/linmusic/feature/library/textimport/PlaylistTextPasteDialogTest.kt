package com.lin0721.linmusic.feature.library.textimport

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class PlaylistTextPasteDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pastedMultilineTextReachesMatchingUnchangedAndBlankCannotSubmit() {
        var submitted: String? = null
        compose.setContent {
            MaterialTheme {
                var text by remember { mutableStateOf("") }
                PlaylistTextPasteDialog(text, { text = it }, {}, { submitted = text })
            }
        }
        compose.onNodeWithText("开始匹配").assertIsNotEnabled()
        val sample = "周杰伦-晴天\nStarlight - Muse\nMuse-Invincible"
        compose.onNodeWithText("歌单文本").performTextReplacement(sample)
        compose.onNodeWithText("开始匹配").performClick()
        compose.runOnIdle { assertEquals(sample, submitted) }
    }

    @Test fun cancelDoesNotStartMatching() {
        var dismissed = false
        var submitted = false
        compose.setContent {
            MaterialTheme {
                PlaylistTextPasteDialog("Muse-Starlight", {}, { dismissed = true }, { submitted = true })
            }
        }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(true, dismissed); assertFalse(submitted) }
    }
}
