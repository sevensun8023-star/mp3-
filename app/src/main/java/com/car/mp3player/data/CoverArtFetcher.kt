package com.car.mp3player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.car.mp3player.model.Song
import com.car.mp3player.util.MediaPath
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CoverArtFetcher(private val context: Context) {
    fun fetch(song: Song, cacheFile: File, allowOnline: Boolean = true, onlineMusicApi: OnlineMusicApi? = null): String? {
        fetchEmbeddedOnly(song, cacheFile)?.let { return it }
        if (!allowOnline) return null
        MediaPath.parseOnline(song.path)?.let { parts ->
            val picId = parts.picId ?: song.lrcPath?.removePrefix("pic:")
            if (!picId.isNullOrBlank()) {
                val url = onlineMusicApi?.fetchCoverUrl(parts.source, picId)
                if (url != null) return downloadToFile(url, cacheFile)
            }
        }
        song.lrcPath?.removePrefix("favicon:")?.takeIf { it.startsWith("http") }?.let { url ->
            return downloadToFile(url, cacheFile)
        }
        val url = searchItunesArtUrl(song.title, song.artist) ?: return null
        return downloadToFile(url, cacheFile)
    }

    fun fetchEmbeddedOnly(song: Song, cacheFile: File): String? {
        cacheFile.parentFile?.mkdirs()
        extractEmbedded(song)?.let { bytes ->
            cacheFile.writeBytes(bytes)
            return cacheFile.absolutePath
        }
        return null
    }

    private fun extractEmbedded(song: Song): ByteArray? {
        if (com.car.mp3player.util.MediaPath.isStream(song.path)) return null
        val retriever = MediaMetadataRetriever()
        return try {
            if (song.path.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(song.path))
            } else {
                retriever.setDataSource(song.path)
            }
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun searchItunesArtUrl(title: String, artist: String): String? {
        val term = listOf(title, artist)
            .filter { it.isNotBlank() && it !in setOf("未知歌手", "本地音乐") }
            .joinToString(" ")
        if (term.isBlank()) return null
        val url = "https://itunes.apple.com/search?term=${encode(term)}&entity=song&limit=1"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        return try {
            val body = connection.inputStream.bufferedReader().readText()
            val results = JSONObject(body).optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            results.optJSONObject(0)
                ?.optString("artworkUrl100")
                ?.replace("100x100bb", "600x600bb")
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadToFile(url: String, file: File): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
        }
        return try {
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
