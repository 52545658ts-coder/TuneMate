package com.example.tunemate.playback

import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.tunemate.equalizer.EqualizerHolder

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizerSessionId = C.AUDIO_SESSION_ID_UNSET

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            setHandleAudioBecomingNoisy(true)
        }

        // 監聽 audio session 建立／變化事件
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupAudioEffects(audioSessionId)
            }
        })

        // 若建構後立即已有 session id（部分裝置會這樣），先嘗試建立
        setupAudioEffects(player.audioSessionId)

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun setupAudioEffects(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (equalizer != null && equalizerSessionId == sessionId) return

        releaseAudioEffects()

        // Equalizer
        try {
            val eq = Equalizer(0, sessionId).apply { enabled = true }
            equalizer = eq
            equalizerSessionId = sessionId
            EqualizerHolder.attach(eq)
        } catch (e: Exception) {
            Log.e("PlaybackService", "建立 Equalizer 失敗", e)
        }

        // LoudnessEnhancer（音量增幅器）
        try {
            val le = LoudnessEnhancer(sessionId).apply {
                setTargetGain(0)  // 預設不增幅
                enabled = true
            }
            loudnessEnhancer = le
            EqualizerHolder.attachBooster(le)
        } catch (e: Exception) {
            Log.e("PlaybackService", "建立 LoudnessEnhancer 失敗", e)
        }
    }

    private fun releaseAudioEffects() {
        equalizer?.let {
            EqualizerHolder.detach(it)
            runCatching { it.enabled = false }
            runCatching { it.release() }
        }
        equalizer = null
        equalizerSessionId = C.AUDIO_SESSION_ID_UNSET

        loudnessEnhancer?.let {
            EqualizerHolder.detachBooster(it)
            runCatching { it.enabled = false }
            runCatching { it.release() }
        }
        loudnessEnhancer = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        releaseAudioEffects()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        player?.stop()
        pauseAllPlayersAndStopSelf()
    }
}
