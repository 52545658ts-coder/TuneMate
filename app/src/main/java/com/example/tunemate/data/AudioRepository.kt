package com.example.tunemate.data

import android.content.ContentResolver
import android.provider.MediaStore
import com.example.tunemate.model.Song

fun queryAudioFiles(resolver: ContentResolver): List<Song> {
    val songs = mutableListOf<Song>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION
    )
    resolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, null, null,
        "${MediaStore.Audio.Media.TITLE} ASC"
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (c.moveToNext()) {
            val id = c.getLong(idCol)
            songs.add(
                Song(
                    id = id,
                    title = c.getString(titleCol) ?: "未知曲目",
                    artist = c.getString(artistCol) ?: "未知演出者",
                    duration = c.getLong(durCol),
                    uriString = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        .buildUpon().appendPath(id.toString()).build().toString()
                )
            )
        }
    }
    return songs
}