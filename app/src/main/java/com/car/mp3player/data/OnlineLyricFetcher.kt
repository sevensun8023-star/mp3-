package com.car.mp3player.data

import com.car.mp3player.lrc.LrcParser
import com.car.mp3player.model.LrcLine
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OnlineLyricFetcher {
    fun fetch(title: String, artist: String): List<LrcLine>? {
        val synced = searchLyrics(title, artist, preferSynced = true)
        if (!synced.isNullOrEmpty()) return synced
        return searchLyrics(title, artist, preferSynced = false)
    }

    private fun searchLyrics(title: String, artist: String, preferSynced: Boolean): List<LrcLine>? {
        val query = buildString {
            append("https://lrclib.net/api/search?")
            append("track_name=").append(encode(title))
            if (artist.isNotBlank() && artist != "未知歌手" && artist != "本地音乐") {
                append("&artist_name=").append(encode(artist))
            }
        }
        val array = runCatching { getJsonArray(query) }.getOrNull() ?: return null
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (preferSynced) {
                val syncedLyrics = item.optString("syncedLyrics").takeIf { it.isNotBlank() } ?: continue
                val lines = LrcParser.parseContent(syncedLyrics)
                if (lines.isNotEmpty()) return lines
            } else {
                val plainLyrics = item.optString("plainLyrics").takeIf { it.isNotBlank() } ?: continue
                val lines = LrcParser.fromPlainText(plainLyrics)
                if (lines.isNotEmpty()) return lines
            }
        }
        return null
    }

    private fun getJsonArray(url: String): JSONArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MP3Player/2.1")
        }
        return try {
            connection.inputStream.bufferedReader().use { reader ->
                JSONArray(reader.readText())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
