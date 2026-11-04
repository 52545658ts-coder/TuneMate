package com.example.tunemate.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.tunemate.model.Playlist
import com.example.tunemate.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val Context.dataStore by preferencesDataStore("tunemate")

private val PLAYLIST_KEY = stringPreferencesKey("playlists")
private val SONGS_KEY = stringPreferencesKey("imported_songs")
private val BG_URI_KEY = stringPreferencesKey("background_uri")
private val BLUR_KEY = floatPreferencesKey("background_blur")

private val PB_SONG_URI = stringPreferencesKey("pb_song_uri")
private val PB_PLAYLIST = stringPreferencesKey("pb_playlist")
private val PB_POSITION = longPreferencesKey("pb_position")
private val PB_PLAY_MODE = stringPreferencesKey("pb_play_mode")

private val json = Json { ignoreUnknownKeys = true }

// ---------- 已匯入歌曲 ----------
fun Context.songsFlow(): Flow<List<Song>> = dataStore.data.map { prefs ->
    prefs[SONGS_KEY]?.let {
        runCatching { json.decodeFromString<List<Song>>(it) }.getOrDefault(emptyList())
    } ?: emptyList()
}

suspend fun Context.saveSongs(songs: List<Song>) {
    dataStore.edit { it[SONGS_KEY] = json.encodeToString(songs) }
}

// ---------- 歌單 ----------
suspend fun Context.savePlaylists(playlists: List<Playlist>) {
    dataStore.edit { it[PLAYLIST_KEY] = json.encodeToString(playlists) }
}

fun Context.playlistsFlow(): Flow<List<Playlist>> = dataStore.data.map { prefs ->
    prefs[PLAYLIST_KEY]?.let {
        runCatching { json.decodeFromString<List<Playlist>>(it) }.getOrDefault(emptyList())
    } ?: emptyList()
}

// ---------- 背景圖 + 模糊度 ----------
data class BackgroundPrefs(val uri: String?, val blur: Float)

suspend fun Context.saveBackground(uri: String?, blur: Float) {
    dataStore.edit { prefs ->
        if (uri == null) prefs.remove(BG_URI_KEY) else prefs[BG_URI_KEY] = uri
        prefs[BLUR_KEY] = blur
    }
}

fun Context.backgroundFlow(): Flow<BackgroundPrefs> = dataStore.data.map { prefs ->
    BackgroundPrefs(
        uri = prefs[BG_URI_KEY],
        blur = prefs[BLUR_KEY] ?: 12f
    )
}

suspend fun Context.saveBackgroundImage(sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
    try {
        val target = File(filesDir, "background.jpg")
        contentResolver.openInputStream(sourceUri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        Uri.fromFile(target)
    } catch (e: Exception) {
        null
    }
}

// ---------- 播放狀態 ----------
/**
 * 上次離開 App 時的播放狀態：
 *  - songUri      正在播放的歌曲 URI
 *  - playlistName 所屬歌單（null 表示來自「所有歌曲」）
 *  - positionMs   播放位置（毫秒）
 *  - playMode     播放模式（REPEAT_ONE / REPEAT_ALL / PLAY_ONCE 的字串）
 */
data class PlaybackState(
    val songUri: String? = null,
    val playlistName: String? = null,
    val positionMs: Long = 0L,
    val playMode: String? = null
)

fun Context.playbackStateFlow(): Flow<PlaybackState> = dataStore.data.map { prefs ->
    PlaybackState(
        songUri = prefs[PB_SONG_URI],
        playlistName = prefs[PB_PLAYLIST],
        positionMs = prefs[PB_POSITION] ?: 0L,
        playMode = prefs[PB_PLAY_MODE]
    )
}

suspend fun Context.savePlaybackState(state: PlaybackState) {
    dataStore.edit { prefs ->
        state.songUri?.let { prefs[PB_SONG_URI] = it } ?: prefs.remove(PB_SONG_URI)
        state.playlistName?.let { prefs[PB_PLAYLIST] = it } ?: prefs.remove(PB_PLAYLIST)
        prefs[PB_POSITION] = state.positionMs
        state.playMode?.let { prefs[PB_PLAY_MODE] = it } ?: prefs.remove(PB_PLAY_MODE)
    }
}

suspend fun Context.clearPlaybackState() {
    dataStore.edit { prefs ->
        prefs.remove(PB_SONG_URI)
        prefs.remove(PB_PLAYLIST)
        prefs.remove(PB_POSITION)
        prefs.remove(PB_PLAY_MODE)
    }
}
