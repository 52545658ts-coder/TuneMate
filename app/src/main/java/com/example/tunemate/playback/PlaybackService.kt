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

    /**
     * 使用者把 App 從最近任務中劃掉時，完全停止：
     *  - 停止播放（不保留位置）
     *  - 清除前台通知（透過 pauseAllPlayersAndStopSelf）
     *  - 停止 service 本身，讓系統可以回收進程
     *
     * 注意：播放狀態已由 UI 層（MainActivity）定期寫入 DataStore，
     * 這裡不需要再持久化。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        player?.stop()
        pauseAllPlayersAndStopSelf()
    }
}
