package com.car.mp3player.data

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.car.mp3player.model.Song
import java.io.File

class MusicScanner(private val context: Context) {
    private val audioExtensions = setOf("mp3", "flac", "m4a", "wav", "ogg", "aac")

    fun scan(): List<Song> {
        val fromMediaStore = scanMediaStore()
        if (fromMediaStore.isNotEmpty()) return fromMediaStore.sortedBy { it.title.lowercase() }
        return scanDirectories(defaultScanPaths()).sortedBy { it.title.lowercase() }
    }

    private fun scanMediaStore(): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC}=1"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.TITLE + " ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: continue
                if (!isAudioFile(path)) continue
                songs.add(
                    Song(
                        id = cursor.getLong(idCol),
                        title = cursor.getString(titleCol) ?: File(path).nameWithoutExtension,
                        artist = cursor.getString(artistCol) ?: "未知歌手",
                        path = path,
                        lrcPath = findLrc(path)
                    )
                )
            }
        }
        return songs
    }

    private fun scanDirectories(paths: List<File>): List<Song> {
        val songs = mutableListOf<Song>()
        var id = 1L
        for (root in paths) {
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { it.isFile && isAudioFile(it.absolutePath) }
                .forEach { file ->
                    songs.add(
                        Song(
                            id = id++,
                            title = file.nameWithoutExtension,
                            artist = file.parentFile?.name ?: "本地音乐",
                            path = file.absolutePath,
                            lrcPath = findLrc(file.absolutePath)
                        )
                    )
                }
        }
        return songs
    }

    private fun defaultScanPaths(): List<File> {
        val paths = mutableListOf<File>()
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { paths.add(it) }
        paths.add(File("/storage/emulated/0/Music"))
        paths.add(File("/sdcard/Music"))
        context.getExternalFilesDirs(null).forEach { dir ->
            dir?.let { paths.add(File(it, "Music")) }
        }
        return paths.distinctBy { it.absolutePath }
    }

    private fun findLrc(audioPath: String): String? {
        val base = audioPath.substringBeforeLast('.')
        val lrc = File("$base.lrc")
        return if (lrc.exists()) lrc.absolutePath else null
    }

    private fun isAudioFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in audioExtensions
    }
}
