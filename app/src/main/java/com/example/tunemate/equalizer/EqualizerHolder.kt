package com.example.tunemate.equalizer

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 跨 PlaybackService 與 UI 共享的音頻效果實例。
 * 使用 Compose 的 mutableStateOf，UI 會自動重組。
 */
object EqualizerHolder {
    var equalizer by mutableStateOf<Equalizer?>(null)
        private set

    var loudnessEnhancer by mutableStateOf<LoudnessEnhancer?>(null)
        private set

    fun attach(eq: Equalizer) { equalizer = eq }

    fun detach(eq: Equalizer) {
        if (equalizer === eq) equalizer = null
    }

    fun attachBooster(le: LoudnessEnhancer) { loudnessEnhancer = le }

    fun detachBooster(le: LoudnessEnhancer) {
        if (loudnessEnhancer === le) loudnessEnhancer = null
    }
}
