package com.example.tunemate.data

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.tunemate.model.Song

/**
 * 從使用者透過 SAF（Storage Access Framework）選擇的音頻 Uri 建立 Song。
 *
 * 使用 MediaMetadataRetriever 讀取標題 / 演出者 / 時長；
 * 若讀不到標題，退回檔案顯示名稱，並去除副檔名。
 */
fun buildSongFromUri(context: Context, uri: Uri): Song? {
    val mmr = MediaMetadataRetriever()
    return try {
        mmr.setDataSource(context, uri)

        // 優先讀取音檔內嵌標題，若無則使用檔案名稱
        val rawTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: queryDisplayName(context.contentResolver, uri)
            ?: "未知曲目"

        // 去除檔案副檔名（例如 .mp3, .flac, .wav）
        val title = removeExtension(rawTitle)

        val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: "未知演出者"

        val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L

        Song(
            id = uri.hashCode().toLong(),
            title = title,
            artist = artist,
            duration = duration,
            uriString = uri.toString()
        )
    } catch (e: Exception) {
        null
    } finally {
        runCatching { mmr.release() }
    }
}

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else {
                null
            }
        }
    }.getOrNull()

/**
 * 移除字串結尾的副檔名。
 * 為了避免誤刪（例如 "Track.01"），我們限制副檔名長度不能超過 5 個字元。
 */
private fun removeExtension(fileName: String): String {
    val lastDotIndex = fileName.lastIndexOf('.')
    // 如果找到點，且點不在開頭，且點後面的長度 <= 5（例如 .mp3, .flac, .m4a）
    return if (lastDotIndex > 0 && fileName.length - lastDotIndex <= 6) {
        fileName.substring(0, lastDotIndex)
    } else {
        fileName
    }
}
