package com.car.mp3player.data

import com.car.mp3player.model.OnlinePlaylistSummary
import com.car.mp3player.model.OnlineTrackRef
import com.car.mp3player.model.Song
import com.car.mp3player.util.MediaPath
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OnlineMusicApi(private val settings: SettingsRepository) {

    data class PlayUrlResult(val url: String, val bitrate: Int)

    fun searchSongs(keyword: String, page: Int = 1, count: Int = 30): List<Song> {
        if (keyword.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(keyword, Charsets.UTF_8.name())
        val body = httpGet(buildUrl("types=search&source=netease&name=$encoded&count=$count&pages=$page"))
            ?: return emptyList()
        return parseSearchArray(body)
    }

    fun searchPlaylists(keyword: String, page: Int = 1, count: Int = 20): List<OnlinePlaylistSummary> {
        if (keyword.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(keyword, Charsets.UTF_8.name())
        val primary = httpGet(buildUrl("types=search&source=netease_playlist&name=$encoded&count=$count&pages=$page"))
        if (primary != null) {
            val parsed = parsePlaylistSearch(primary)
            if (parsed.isNotEmpty()) return parsed
        }
        return searchPlaylistsViaPlaylistApi(keyword, count)
    }

    fun loadPlaylist(playlistId: String): Pair<OnlinePlaylistSummary?, List<Song>> {
        val id = extractPlaylistId(playlistId) ?: return null to emptyList()
        val body = httpGet(buildUrl("types=playlist&source=netease&id=$id")) ?: return null to emptyList()
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null to emptyList()
        val playlist = json.optJSONObject("playlist") ?: return null to emptyList()
        val summary = OnlinePlaylistSummary(
            id = playlist.optString("id", id),
            name = playlist.optString("name"),
            coverUrl = playlist.optString("coverImgUrl").takeIf { it.isNotBlank() },
            trackCount = playlist.optInt("trackCount"),
            playCount = playlist.optLong("playCount"),
            subscribedCount = playlist.optLong("subscribedCount")
        )
        val tracks = playlist.optJSONArray("tracks")?.let { parsePlaylistTracks(it) }
            ?: emptyList()
        return summary to tracks
    }

    fun resolvePlayUrl(song: Song): PlayUrlResult? {
        val parts = MediaPath.parseOnline(song.path) ?: return null
        return resolvePlayUrl(parts.source, parts.trackId)
    }

    fun resolvePlayUrl(source: String, trackId: String): PlayUrlResult? {
        val br = settings.onlineMusicQuality
        val body = httpGet(buildUrl("types=url&source=$source&id=$trackId&br=$br")) ?: return null
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (json != null) {
            val url = json.optString("url").takeIf { it.isNotBlank() && it.startsWith("http") }
            if (url != null) return PlayUrlResult(url, json.optInt("br", br))
        }
        return null
    }

    fun fetchLyric(source: String, lyricId: String): String? {
        val body = httpGet(buildUrl("types=lyric&source=$source&id=$lyricId")) ?: return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return json.optString("lyric").takeIf { it.isNotBlank() }
    }

    fun fetchCoverUrl(source: String, picId: String, size: Int = 500): String? {
        val body = httpGet(buildUrl("types=pic&source=$source&id=$picId&size=$size")) ?: return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return json.optString("url").takeIf { it.startsWith("http") }
    }

    fun matchTrackOnApi(ref: OnlineTrackRef): OnlineTrackRef? {
        val results = searchSongs("${ref.title} ${ref.artist}", count = 8)
        val best = results.firstOrNull { song ->
            normalize(song.title) == normalize(ref.title) &&
                normalize(song.artist).contains(normalize(ref.artist.split("/").first()).take(2))
        } ?: results.firstOrNull { normalize(it.title) == normalize(ref.title) }
        ?: return null
        val parts = MediaPath.parseOnline(best.path) ?: return null
        return ref.copy(
            source = parts.source,
            trackId = parts.trackId,
            picId = parts.picId ?: ref.picId
        )
    }

    fun refFromSong(song: Song): OnlineTrackRef? {
        val parts = MediaPath.parseOnline(song.path) ?: return null
        return OnlineTrackRef(
            localId = MediaPath.newLocalId(),
            title = song.title,
            artist = song.artist,
            album = parts.album.orEmpty(),
            source = parts.source,
            trackId = parts.trackId,
            picId = parts.picId ?: song.lrcPath?.removePrefix("pic:"),
            durationMs = song.durationMs
        )
    }

    private fun buildUrl(query: String): String {
        val base = settings.onlineMusicApiUrl.trimEnd('/')
        val separator = if (base.contains('?')) "&" else "?"
        return if (base.endsWith(".php")) "$base?$query" else "$base$separator$query"
    }

    private fun parseSearchArray(body: String): List<Song> {
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val artists = item.optJSONArray("artist")?.let { arr ->
                    (0 until arr.length()).joinToString("/") { idx -> arr.optString(idx) }
                }.orEmpty()
                add(
                    MediaPath.songFromOnlineSearch(
                        source = item.optString("source", "netease"),
                        trackId = item.optString("id"),
                        title = item.optString("name"),
                        artist = artists.ifBlank { "未知歌手" },
                        album = item.optString("album"),
                        picId = normalizePicId(
                            item.optString("pic_id"),
                            item.optLong("picId").takeIf { it > 0L }?.toString()
                        )
                    )
                )
            }
        }
    }

    private fun parsePlaylistSearch(body: String): List<OnlinePlaylistSummary> {
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    OnlinePlaylistSummary(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        coverUrl = item.optString("pic").takeIf { it.startsWith("http") }
                            ?: item.optString("coverImgUrl").takeIf { it.startsWith("http") },
                        trackCount = item.optInt("trackCount", item.optInt("songCount")),
                        playCount = item.optLong("playCount"),
                        subscribedCount = item.optLong("subscribedCount")
                    )
                )
            }
        }
    }

    private fun searchPlaylistsViaPlaylistApi(keyword: String, count: Int): List<OnlinePlaylistSummary> {
        val encoded = URLEncoder.encode(keyword, Charsets.UTF_8.name())
        val url = "https://music.163.com/api/search/get/web?s=$encoded&type=1000&offset=0&limit=$count"
        val body = httpGet(url, mapOf("Referer" to "https://music.163.com/")) ?: return emptyList()
        val playlists = runCatching { JSONObject(body) }
            .getOrNull()
            ?.optJSONObject("result")
            ?.optJSONArray("playlists")
            ?: return emptyList()
        return buildList {
            for (i in 0 until playlists.length()) {
                val item = playlists.optJSONObject(i) ?: continue
                add(
                    OnlinePlaylistSummary(
                        id = item.optLong("id").toString(),
                        name = item.optString("name"),
                        coverUrl = item.optString("coverImgUrl").takeIf { it.isNotBlank() },
                        trackCount = item.optInt("trackCount"),
                        playCount = item.optLong("playCount"),
                        subscribedCount = item.optLong("bookCount")
                    )
                )
            }
        }
    }

    private fun parsePlaylistTracks(array: JSONArray): List<Song> {
        return buildList {
            for (i in 0 until array.length()) {
                val track = array.optJSONObject(i) ?: continue
                val artists = track.optJSONArray("ar")?.let { arr ->
                    (0 until arr.length()).joinToString("/") { idx ->
                        arr.optJSONObject(idx)?.optString("name").orEmpty()
                    }
                }.orEmpty()
                val album = track.optJSONObject("al")
                val picId = normalizePicId(
                    album?.optLong("picId")?.toString(),
                    track.optLong("picId").takeIf { it > 0L }?.toString()
                )
                add(
                    MediaPath.songFromOnlineSearch(
                        source = "netease",
                        trackId = track.optLong("id").toString(),
                        title = track.optString("name"),
                        artist = artists.ifBlank { "未知歌手" },
                        album = album?.optString("name").orEmpty(),
                        picId = picId,
                        durationMs = track.optLong("dt")
                    )
                )
            }
        }
    }

    private fun extractPlaylistId(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.all { it.isDigit() }) return trimmed
        Regex("id=(\\d+)").find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        Regex("(\\d{6,})").find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("\\s+"), "").replace(Regex("[()（）\\[\\]]"), "")

    private fun normalizePicId(vararg candidates: String?): String? {
        for (candidate in candidates) {
            val value = candidate?.trim()?.takeIf { it.isNotBlank() && it != "0" } ?: continue
            return value
        }
        return null
    }

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String? {
        val bases = listOfNotNull(
            settings.onlineMusicApiUrl.takeIf { it.isNotBlank() },
            settings.onlineMusicApiBackup.takeIf { it.isNotBlank() }
        ).distinct()
        for (base in bases) {
            val resolved = if (url.startsWith("http")) url else {
                val q = url.substringAfter('?', url)
                val endpoint = base.trimEnd('/')
                if (endpoint.endsWith(".php")) "$endpoint?$q" else "$endpoint?$q"
            }
            val result = httpGetOnce(resolved, headers)
            if (result != null) return result
        }
        if (url.startsWith("http")) return httpGetOnce(url, headers)
        return null
    }

    private fun httpGetOnce(url: String, headers: Map<String, String>): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val USER_AGENT =
            "MP3Player/4.0 (Android; Car) Mozilla/5.0 Mobile Safari/537.36"
    }
}
