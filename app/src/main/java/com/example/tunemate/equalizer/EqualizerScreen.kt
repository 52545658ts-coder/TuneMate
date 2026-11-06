package com.example.tunemate.equalizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** 音量增幅器上限：0 ~ +15 dB（單位：毫貝 mB，1 dB = 100 mB） */
private const val MAX_BOOSTER_GAIN_MB: Int = 1500

@Composable
fun EqualizerScreen() {
    val eq = EqualizerHolder.equalizer
    val accentColor = MaterialTheme.colorScheme.primary
    val onBg = MaterialTheme.colorScheme.onBackground

    if (eq == null) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "均衡器",
                style = MaterialTheme.typography.titleLarge,
                color = accentColor
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "均衡器會在音樂開始播放後自動啟用。\n請先回到播放器播放一首歌，再回來調整。",
                color = onBg.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val numBands: Int = eq.numberOfBands.toInt()
    val range = eq.bandLevelRange
    val minLevel: Int = range[0].toInt()
    val maxLevel: Int = range[1].toInt()

    val bandLevels = remember(eq) {
        mutableStateListOf<Int>().apply {
            for (b in 0 until numBands) add(eq.getBandLevel(b.toShort()).toInt())
        }
    }
    var enabled by remember(eq) { mutableStateOf(eq.enabled) }
    var currentPreset by remember(eq) { mutableIntStateOf(eq.currentPreset.toInt()) }

    // ---------- 音量增幅器 state ----------
    val booster = EqualizerHolder.loudnessEnhancer
    val initialGain: Int = if (booster != null) {
        val raw: Number = booster.getTargetGain()
        raw.toInt()
    } else {
        0
    }
    val clampedInitialGain: Int = when {
        initialGain < 0 -> 0
        initialGain > MAX_BOOSTER_GAIN_MB -> MAX_BOOSTER_GAIN_MB
        else -> initialGain
    }
    var boosterGain by remember(booster) { mutableIntStateOf(clampedInitialGain) }

    Column(Modifier.fillMaxWidth()) {
        // ============ 均衡器 標題 + 開關 ============
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "均衡器",
                style = MaterialTheme.typography.titleLarge,
                color = accentColor
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

        // ============ 低 / 中 / 高音提示 (均分寬度) ============
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("低音", color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Text("中音", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("高音", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = onBg.copy(alpha = 0.2f))
        
        // ============ 垂直滑桿區塊 ============
        Row(
            Modifier
                .fillMaxWidth()
                .height(450.dp)
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (b in 0 until numBands) {
                val centerHz: Float = eq.getCenterFreq(b.toShort()) / 1000f
                val level: Int = bandLevels[b]
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // 頂部數值
                    Text(
                        text = "%+.0f".format(level.toFloat() / 100f),
                        color = onBg.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    // 垂直滑桿
                    VerticalBandSlider(
                        level = level,
                        minLevel = minLevel,
                        maxLevel = maxLevel,
                        modifier = Modifier.weight(1f), 
                        onValueChange = { v: Int ->
                            bandLevels[b] = v
                            runCatching { eq.setBandLevel(b.toShort(), v.toShort()) }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    // 底部頻率
                    Text(
                        text = formatHz(centerHz),
                        color = bandColor(centerHz, accentColor),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        OutlinedButton(onClick = {
            for (b in 0 until numBands) {
                bandLevels[b] = 0
                runCatching { eq.setBandLevel(b.toShort(), 0) }
            }
        }) { Text("全部歸零") }

        // ============ 音量增幅器 ============
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = onBg.copy(alpha = 0.2f))
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "音量增幅",
                style = MaterialTheme.typography.titleMedium,
                color = accentColor
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (booster == null) "未啟用"
                else "%+.1f dB".format(boosterGain.toFloat() / 100f),
                color = if (booster == null) Color.Gray else onBg,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))

        if (booster == null) {
            Text(
                "音量增幅器會在音樂開始播放後自動啟用。",
                color = onBg.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Slider(
                value = boosterGain.toFloat(),
                onValueChange = { v: Float ->
                    val iv: Int = v.toInt()
                    val clamped: Int = when {
                        iv < 0 -> 0
                        iv > MAX_BOOSTER_GAIN_MB -> MAX_BOOSTER_GAIN_MB
                        else -> iv
                    }
                    boosterGain = clamped
                    runCatching {
                        booster.setTargetGain(clamped)
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
                Text("0 dB", color = onBg.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                Text("%.1f dB".format(MAX_BOOSTER_GAIN_MB.toFloat() / 100f), color = onBg.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                boosterGain = 0
                runCatching { booster.setTargetGain(0) }
            }) { Text("重置增幅") }
        }

        // ============ 預設音效 ============
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = onBg.copy(alpha = 0.2f))
        Spacer(Modifier.height(16.dp))

        Text("預設音效", color = onBg, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        val presetCount: Int = eq.numberOfPresets.toInt()
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
                Text(name, color = onBg)
            }
        }
    }
}

/* ============================================================
 * 完美的垂直滑桿：透過 Layout 交換寬高限制
 * ============================================================ */
@Composable
private fun VerticalBandSlider(
    level: Int,
    minLevel: Int,
    maxLevel: Int,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = level.toFloat(),
            onValueChange = { v: Float -> onValueChange(v.toInt()) },
            valueRange = minLevel.toFloat()..maxLevel.toFloat(),
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(
                            x = (placeable.height - placeable.width) / 2,
                            y = (placeable.width - placeable.height) / 2
                        )
                    }
                }
                .graphicsLayer {
                    rotationZ = -90f
                }
        )
    }
}

private fun bandColor(hz: Float, accentColor: Color): Color = when {
    hz < 500f -> accentColor            // 低音使用主題色
    hz < 4000f -> Color(0xFF81C784)     // 中音保持綠色
    else -> Color(0xFFFFB74D)           // 高音保持橙色
}

private fun formatHz(hz: Float): String =
    if (hz >= 1000f) "%.1fk".format(hz / 1000f) else "${hz.toInt()}Hz"
