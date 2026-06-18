package com.car.mp3player.data

import com.car.mp3player.lrc.LrcParser
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.Song
import java.io.File
import java.security.MessageDigest

class SongMetadataLoader(
    private val cacheDir: File,
    private val settings: SettingsRepository,
    private val lyricFetcher: OnlineLyricFetcher = OnlineLyricFetcher(),
    private val coverFetcher: CoverArtFetcher
) {
    fun loadLyrics(song: Song): List<LrcLine>? {
        song.lrcPath?.let { path ->
            runCatching { LrcParser.parseFile(File(path)) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                return it
            }
        }
        val cache = lyricsCacheFile(song)
        if (cache.exists()) {
            runCatching { LrcParser.parseFile(cache) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                return it
            }
        }
        if (!settings.onlineLyricsEnabled) return null
        val fetched = lyricFetcher.fetch(song.title, song.artist) ?: return null
        runCatching { cache.writeText(buildCacheLrc(fetched)) }
        return fetched
    }

    fun loadCover(song: Song): String? {
        val cache = coverCacheFile(song)
        if (cache.exists() && cache.length() > 0) return cache.absolutePath
        return coverFetcher.fetch(song, cache, allowOnline = settings.onlineCoverEnabled)
    }

    private fun buildCacheLrc(lines: List<LrcLine>): String {
        return lines.joinToString("\n") { line ->
            val tag = formatTime(line.startTimeMs)
            "$tag${line.text}"
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val centi = (ms % 1000) / 10
        return "[${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}.${centi.toString().padStart(2, '0')}]"
    }

    private fun lyricsCacheFile(song: Song): File =
        File(File(cacheDir, "lyrics"), "${songKey(song)}.lrc")

    private fun coverCacheFile(song: Song): File =
        File(File(cacheDir, "covers"), "${songKey(song)}.jpg")

    private fun songKey(song: Song): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest("${song.path}|${song.title}|${song.artist}".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
