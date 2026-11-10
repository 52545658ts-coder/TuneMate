package com.example.tunemate.model

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uriString: String,
    val coverUriString: String? = null // 專輯封面本地路徑
) {
    val uri: Uri get() = Uri.parse(uriString)
}

@Serializable
data class Playlist(
    val name: String,
    val songs: List<Song>
)
