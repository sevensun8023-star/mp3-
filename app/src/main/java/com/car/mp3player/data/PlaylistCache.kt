package com.car.mp3player.data

import android.content.Context
import com.car.mp3player.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PlaylistCache {
    private const val CACHE_FILE = "playlist_cache.json"
    private const val QUEUE_FILE = "play_queue.json"

    fun save(context: Context, songs: List<Song>) = writeSongs(context, CACHE_FILE, songs)

    fun load(context: Context): List<Song> = readSongs(context, CACHE_FILE)

    /** 当前正在播放的队列（可能是歌手子集），避免通过 Intent 传递大列表导致闪退 */
    fun saveQueue(context: Context, songs: List<Song>) = writeSongs(context, QUEUE_FILE, songs)

    fun loadQueue(context: Context): List<Song> = readSongs(context, QUEUE_FILE)

    private fun writeSongs(context: Context, fileName: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        val array = JSONArray()
        songs.forEach { song ->
            array.put(
                JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("path", song.path)
                    put("lrcPath", song.lrcPath)
                    put("durationMs", song.durationMs)
                }
            )
        }
        runCatching {
            File(context.filesDir, fileName).writeText(array.toString())
        }
    }

    private fun readSongs(context: Context, fileName: String): List<Song> {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        Song(
                            id = item.optLong("id"),
                            title = item.optString("title"),
                            artist = item.optString("artist"),
                            path = item.optString("path"),
                            lrcPath = item.optString("lrcPath").takeIf { it.isNotBlank() },
                            durationMs = item.optLong("durationMs")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
