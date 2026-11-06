package com.example.tunemate.equalizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    val booster = EqualizerHolder.loudnessEnhancer
    var boosterGain by remember(booster) {
        mutableIntStateOf(booster?.targetGain?.coerceIn(0, MAX_BOOSTER_GAIN_MB) ?: 0)
    }

    Column(Modifier.fillMaxWidth()) {
        // ===== 均衡器 標題 + 開關 =====
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

        // ===== 低 / 中 / 高音提示 =====
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("低音", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
            Text("中音", color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
            Text("高音", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
        Spacer(Modifier.height(12.dp))

        // ===== 垂直滑桿：band 由左至右排列 =====
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top
        ) {
            for (b in 0 until numBands) {
                val centerHz = eq.getCenterFreq(b.toShort()) / 1000f  // mHz → Hz
                val level = bandLevels[b]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(56.dp)
                ) {
                    Text(
                        "%+.0f".format(level / 100f),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    VerticalBandSlider(
                        level = level,
                        minLevel = minLevel,
                        maxLevel = maxLevel,
                        onValueChange = { v ->
                            bandLevels[b] = v
                            runCatching { eq.setBandLevel(b.toShort(), v.toShort()) }
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatHz(centerHz),
                        color = bandColor(centerHz),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {
            for (b in 0 until numBands) {
                bandLevels[b] = 0
                runCatching { eq.setBandLevel(b.toShort(), 0) }
            }
        }) { Text("全部歸零") }

        // ===== 音量增幅器 =====
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "音量增幅",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF4FC3F7)
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (booster == null) "未啟用"
                else "%+.1f dB".format(boosterGain / 100f),
                color = if (booster == null) Color.Gray else Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))

        if (booster == null) {
            Text(
                "音量增幅器會在音樂開始播放後自動啟用。",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Slider(
                value = boosterGain.toFloat(),
                onValueChange = { v ->
                    val iv = v.toInt().coerceIn(0, MAX_BOOSTER_GAIN_MB)
                    boosterGain = iv
                    runCatching {
                        booster.setTargetGain(iv)
                        if (!booster.enabled) booster.enabled = true
                    }
                },
                valueRange = 0f..MAX_BOOSTER_GAIN_MB.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "0 dB",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "%.1f dB".format(MAX_BOOSTER_GAIN_MB / 100f),
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                boosterGain = 0
                runCatching { booster.setTargetGain(0) }
            }) { Text("重置增幅") }
        }

        // ===== 預設音效 =====
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
        Spacer(Modifier.height(16.dp))

        Text("預設音效", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        val presetCount = eq.numberOfPresets.toInt()
        for (p in 0 until presetCount) {
            val name = runCatching { eq.getPresetName(p.toShort()) }
                .getOrDefault("Preset $p")
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

/* ============================================================
 * 垂直滑桿：把水平 Slider 旋轉 -90°，
 * 讓「值變大 = 往上」符合均衡器直覺。
 * ============================================================ */
@Composable
private fun VerticalBandSlider(
    level: Int,
    minLevel: Int,
    maxLevel: Int,
    onValueChange: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = level.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = minLevel.toFloat()..maxLevel.toFloat(),
            modifier = Modifier
                .width(200.dp)
                .graphicsLayer { rotationZ = -90f }
        )
    }
}

/* 0 ~ +15 dB（單位：毫貝 mB，1 dB = 100 mB） */
private const val MAX_BOOSTER_GAIN_MB = 1500

private fun bandColor(hz: Float): Color = when {
    hz < 500f -> Color(0xFF4FC3F7)   // 低音
    hz < 4000f -> Color(0xFF81C784)  // 中音
    else -> Color(0xFFFFB74D)        // 高音
}

private fun formatHz(hz: Float): String =
    if (hz >= 1000f) "%.1fk".format(hz / 1000f) else "${hz.toInt()}Hz"
