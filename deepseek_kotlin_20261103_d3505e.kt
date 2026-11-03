package com.example.tunemate.tuner

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

private val NOTES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** 自相关法音高检测 */
class PitchDetector(private val sampleRate: Int) {
    fun detect(buffer: ShortArray): Float {
        val size = buffer.size
        val minLag = (sampleRate / 1000.0).toInt()
        val maxLag = (sampleRate / 60.0).toInt().coerceAtMost(size - 1)
        var bestLag = -1
        var bestScore = 0.0
        for (lag in minLag..maxLag) {
            var corr = 0.0
            var norm = 0.0
            for (i in 0 until size - lag) {
                corr += buffer[i].toDouble() * buffer[i + lag]
                norm += buffer[i].toDouble() * buffer[i]
            }
            if (norm <= 0.0) continue
            val score = corr / norm
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        return if (bestLag > 0 && bestScore > 0.4) sampleRate.toFloat() / bestLag else 0f
    }
}

@Composable
fun TunerScreen() {
    var hasPermission by remember { mutableStateOf(false) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var note by remember { mutableStateOf("—") }
    var cents by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val sampleRate = 44100
            val bufSize = 2048
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(bufSize * 2)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
            } catch (e: Exception) {
                null
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext
            }

            val detector = PitchDetector(sampleRate)
            val buffer = ShortArray(bufSize)
            recorder.startRecording()
            try {
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val hz = detector.detect(buffer)
                        if (hz > 0f) {
                            pitch = hz
                            val midi = 69 + 12 * ln(hz / 440.0) / ln(2.0)
                            val rounded = midi.roundToInt()
                            note = NOTES[((rounded % 12) + 12) % 12]
                            cents = ((midi - rounded) * 100).roundToInt()
                        }
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("调音器", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(32.dp))

        Box(
            Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(
                    when {
                        pitch <= 0f -> Color(0xFF333344)
                        abs(cents) <= 5 -> Color(0xFF2E7D32)
                        else -> Color(0xFF8D6E00)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    note,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (pitch > 0f) {
                    Text(
                        "%.1f Hz".format(pitch),
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            when {
                !hasPermission -> "请允许麦克风权限"
                pitch <= 0f -> "等待声音…"
                cents > 5 -> "偏高 +${cents}¢"
                cents < -5 -> "偏低 ${cents}¢"
                else -> "准确 ✓"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "靠近乐器发出稳定声音，检测范围约 60Hz – 1000Hz",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}