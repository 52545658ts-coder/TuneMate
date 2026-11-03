package com.example.tunemate.equalizer

import android.media.audiofx.Equalizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 跨 PlaybackService 與 UI 共享的 Equalizer 實例。
 * 使用 Compose 的 mutableStateOf，UI 會自動重組。
 */
object EqualizerHolder {
    var equalizer by mutableStateOf<Equalizer?>(null)
        private set

    fun attach(eq: Equalizer) { equalizer = eq }

    fun detach(eq: Equalizer) {
        if (equalizer === eq) equalizer = null
    }
}