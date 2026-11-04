package com.example.tunemate.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

private val json = Json { ignoreUnknownKeys = true }

// ---------- 已匯入歌曲 ----------
/**
 * 回傳使用者「手動匯入」的歌曲列表。
 * 與舊版自動掃描 MediaStore 不同，這裡只包含使用者透過系統檔案選擇器
 * 明確挑選過的音頻。
 */
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
