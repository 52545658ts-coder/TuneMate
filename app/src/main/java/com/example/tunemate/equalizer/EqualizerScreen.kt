package com.example.tunemate.equalizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EqualizerScreen() {
    val eq = EqualizerHolder.equalizer

    if (eq == null) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "均衡器",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF4FC3F7)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "均衡器會在音樂開始播放後自動啟用。\n請先回到播放器播放一首歌，再回來調整。",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val numBands = eq.numberOfBands.toInt()
    val range = eq.bandLevelRange
    val minLevel = range[0].toInt()
    val maxLevel = range[1].toInt()

    val bandLevels = remember(eq) {
        mutableStateListOf<Int>().apply {
            for (b in 0 until numBands) add(eq.getBandLevel(b.toShort()).toInt())
        }
    }
    var enabled by remember(eq) { mutableStateOf(eq.enabled) }
    var currentPreset by remember(eq) { mutableIntStateOf(eq.currentPreset.toInt()) }

    Column(Modifier.fillMaxWidth()) {
        // 标题 + 开关
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "均衡器",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF4FC3F7)
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    runCatching { eq.enabled = it }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        // 低 / 中 / 高音提示
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("低音", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
            Text("中音", color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
            Text("高音", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
        Spacer(Modifier.height(8.dp))

        // 每个 band 一个滑杆
        for (b in 0 until numBands) {
            val centerHz = eq.getCenterFreq(b.toShort()) / 1000f // mHz → Hz
            val level = bandLevels[b]
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatHz(centerHz),
                        color = bandColor(centerHz),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(72.dp)
                    )
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { v ->
                            val iv = v.toInt()
                            bandLevels[b] = iv
                            runCatching { eq.setBandLevel(b.toShort(), iv.toShort()) }
                        },
                        valueRange = minLevel.toFloat()..maxLevel.toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "%+.0f dB".format(level / 100f),
                        color = Color.LightGray,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            for (b in 0 until numBands) {
                bandLevels[b] = 0
                runCatching { eq.setBandLevel(b.toShort(), 0) }
            }
        }) { Text("全部歸零") }

        Spacer(Modifier.height(20.dp))
        Text("預設音效", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        val presetCount = eq.numberOfPresets.toInt()
        for (p in 0 until presetCount) {
            val name = runCatching { eq.getPresetName(p.toShort()) }.getOrDefault("Preset $p")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            eq.usePreset(p.toShort())
                            currentPreset = p
                            for (b in 0 until numBands) {
                                bandLevels[b] = eq.getBandLevel(b.toShort()).toInt()
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentPreset == p,
                    onClick = {
                        runCatching {
                            eq.usePreset(p.toShort())
                            currentPreset = p
                            for (b in 0 until numBands) {
                                bandLevels[b] = eq.getBandLevel(b.toShort()).toInt()
                            }
                        }
                    }
                )
                Text(name, color = Color.White)
            }
        }
    }
}

private fun bandColor(hz: Float): Color = when {
    hz < 500f -> Color(0xFF4FC3F7)   // 低音
    hz < 4000f -> Color(0xFF81C784)  // 中音
    else -> Color(0xFFFFB74D)        // 高音
}

private fun formatHz(hz: Float): String =
    if (hz >= 1000f) "%.1fk".format(hz / 1000f) else "${hz.toInt()}Hz"
