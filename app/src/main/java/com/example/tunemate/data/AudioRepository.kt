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
 * 若讀不到標題，退回檔案顯示名稱。
 *
 * id 使用 uri.hashCode() 作為穩定標識（僅用於 UI key / 去重輔助，
 * 真正判斷同一首歌仍以 uriString 為準）。
 */
fun buildSongFromUri(context: Context, uri: Uri): Song? {
    val mmr = MediaMetadataRetriever()
    return try {
        mmr.setDataSource(context, uri)

        val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: queryDisplayName(context.contentResolver, uri)
            ?: "未知曲目"

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
