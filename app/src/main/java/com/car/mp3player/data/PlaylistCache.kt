package com.car.mp3player.data

import android.content.Context
import com.car.mp3player.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PlaylistCache {
    private const val CACHE_FILE = "playlist_cache.json"

    fun save(context: Context, songs: List<Song>) {
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
            File(context.filesDir, CACHE_FILE).writeText(array.toString())
        }
    }

    fun load(context: Context): List<Song> {
        val file = File(context.filesDir, CACHE_FILE)
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
