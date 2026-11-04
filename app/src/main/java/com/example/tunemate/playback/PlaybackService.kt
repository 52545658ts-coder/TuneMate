package com.example.tunemate.playback

import android.content.Intent
import android.media.audiofx.Equalizer
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
                setupEqualizer(audioSessionId)
            }
        })

        // 若建構後立即已有 session id（部分裝置會這樣），先嘗試建立
        setupEqualizer(player.audioSessionId)

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun setupEqualizer(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (equalizer != null && equalizerSessionId == sessionId) return

        releaseEqualizer()
        try {
            val eq = Equalizer(0, sessionId).apply { enabled = true }
            equalizer = eq
            equalizerSessionId = sessionId
            EqualizerHolder.attach(eq)
        } catch (e: Exception) {
            Log.e("PlaybackService", "建立 Equalizer 失敗", e)
        }
    }

    private fun releaseEqualizer() {
        equalizer?.let {
            EqualizerHolder.detach(it)
            // 先關閉再釋放，避免部分裝置出現短暫爆音
            runCatching { it.enabled = false }
            runCatching { it.release() }
        }
        equalizer = null
        equalizerSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        releaseEqualizer()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.isPlaying) {
            pauseAllPlayersAndStopSelf()
        }
    }
}
