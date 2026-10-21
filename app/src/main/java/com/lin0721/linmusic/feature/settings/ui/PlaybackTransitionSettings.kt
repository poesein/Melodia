package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.player.CrossfadePolicy

@Composable
internal fun PlaybackTransitionSettings(smooth: Boolean, durationMs: Int, trimSilence: Boolean,
    onModeChange: (Boolean) -> Unit, onDurationChange: (Int) -> Unit, onTrimChange: (Boolean) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(durationMs) { mutableFloatStateOf(durationMs.toFloat()) }
    Text("歌曲衔接", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
    listOf(false to "原始无缝", true to "平滑衔接").forEach { (value, title) ->
        Row(Modifier.fillMaxWidth().selectable(selected = smooth == value, role = Role.RadioButton,
            onClick = { onModeChange(value) }).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = smooth == value, onClick = null)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(if (value) "提前准备下一首，用短时间重叠衔接" else "完整播放，不重叠，保留录音中的静音",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (smooth) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("重叠时间")
            TextButton(onClick = { editing = true }) { Text(CrossfadePolicy.durationLabel(draft.toInt())) }
        }
        Slider(value = draft.coerceIn(50f, 12_000f), onValueChange = { draft = it },
            onValueChangeFinished = { onDurationChange(CrossfadePolicy.normalizeDurationMs(draft.toInt())) },
            valueRange = 50f..12_000f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(100, 200, 500, 1000).forEach { ms ->
                TextButton(onClick = { onDurationChange(ms) }) { Text(CrossfadePolicy.durationLabel(ms)) }
            }
        }
        Text("点击时间可精确输入，范围 0.05–12 秒", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsSwitchRow(title = "缩短首尾静音", subtitle = "只在平滑模式生效；关闭可保留录音中的停顿",
            checked = trimSilence, onCheckedChange = onTrimChange)
    }
    if (editing) {
        var text by remember { mutableStateOf((durationMs / 1000.0).toString()) }
        val parsed = CrossfadePolicy.parseSeconds(text)
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("重叠时间") }, text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("秒（0.05–12）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                isError = parsed == null)
        }, confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onDurationChange); editing = false }) { Text("应用") }
        }, dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } })
    }
}
