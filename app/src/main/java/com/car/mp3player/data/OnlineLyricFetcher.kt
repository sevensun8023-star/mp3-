package com.car.mp3player.data

import com.car.mp3player.lrc.LrcParser
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.Song
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OnlineLyricFetcher {
    fun fetch(title: String, artist: String): List<LrcLine>? {
        val queries = buildSearchQueries(title, artist)
        for ((track, singer) in queries) {
            val lines = fetchBestMatch(track, singer)
            if (!lines.isNullOrEmpty()) return lines
        }
        return null
    }

    private fun buildSearchQueries(title: String, artist: String): List<Pair<String, String>> {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        val validArtist = isUsefulArtist(cleanArtist)
        return listOfNotNull(
            cleanTitle to cleanArtist,
            cleanTitle to "",
            if (validArtist) cleanTitle to cleanArtist.substringBefore(" feat") else null,
            normalizeTitle(cleanTitle) to if (validArtist) cleanArtist else ""
        ).distinct()
    }

    private fun fetchBestMatch(title: String, artist: String): List<LrcLine>? {
        val array = runCatching { searchApi(title, artist) }.getOrNull() ?: return null
        if (array.length() == 0) return null

        var bestScore = -1
        var bestSynced: String? = null
        var bestPlain: String? = null

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val score = scoreResult(item, title, artist)
            if (score <= bestScore) continue
            bestScore = score
            bestSynced = item.optString("syncedLyrics").takeIf { it.isNotBlank() }
            bestPlain = item.optString("plainLyrics").takeIf { it.isNotBlank() }
        }

        bestSynced?.let { synced ->
            val lines = LrcParser.parseContent(synced)
            if (lines.isNotEmpty()) return lines
        }
        bestPlain?.let { plain ->
            val lines = LrcParser.fromPlainText(plain)
            if (lines.isNotEmpty()) return lines
        }
        return null
    }

    private fun scoreResult(item: org.json.JSONObject, title: String, artist: String): Int {
        val resultTitle = item.optString("trackName", item.optString("name"))
        val resultArtist = item.optString("artistName", item.optString("artist"))
        var score = 0
        score += similarity(title, resultTitle) * 3
        if (isUsefulArtist(artist)) {
            score += similarity(artist, resultArtist) * 2
        }
        if (item.optString("syncedLyrics").isNotBlank()) score += 5
        return score
    }

    private fun similarity(a: String, b: String): Int {
        val left = normalizeTitle(a)
        val right = normalizeTitle(b)
        if (left.isEmpty() || right.isEmpty()) return 0
        if (left == right) return 10
        if (left.contains(right) || right.contains(left)) return 7
        return if (left.take(4) == right.take(4)) 3 else 0
    }

    private fun normalizeTitle(value: String): String {
        return value.lowercase()
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\s+"), "")
            .trim()
    }

    private fun isUsefulArtist(artist: String): Boolean {
        return artist.isNotBlank() && artist !in IGNORED_ARTISTS
    }

    private fun searchApi(title: String, artist: String): JSONArray {
        val query = buildString {
            append("https://lrclib.net/api/search?")
            append("track_name=").append(encode(title))
            if (isUsefulArtist(artist)) {
                append("&artist_name=").append(encode(artist))
            }
        }
        return getJsonArray(query)
    }

    private fun getJsonArray(url: String): JSONArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MP3Player/3.1")
            setRequestProperty("Accept", "application/json")
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

    companion object {
        private val IGNORED_ARTISTS = setOf("未知歌手", "本地音乐", "<unknown>", "Unknown")
    }
}
