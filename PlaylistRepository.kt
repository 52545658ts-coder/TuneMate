package com.example.tunemate.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.tunemate.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("tunemate")

private val PLAYLIST_KEY = stringPreferencesKey("playlists")
private val BG_URI_KEY = stringPreferencesKey("background_uri")
private val BLUR_KEY = floatPreferencesKey("background_blur")

private val json = Json { ignoreUnknownKeys = true }

// ---------- 歌单 ----------
suspend fun Context.savePlaylists(playlists: List<Playlist>) {
    dataStore.edit { it[PLAYLIST_KEY] = json.encodeToString(playlists) }
}

fun Context.playlistsFlow(): Flow<List<Playlist>> = dataStore.data.map { prefs ->
    prefs[PLAYLIST_KEY]?.let {
        runCatching { json.decodeFromString<List<Playlist>>(it) }.getOrDefault(emptyList())
    } ?: emptyList()
}

// ---------- 背景图 + 模糊度 ----------
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