package com.car.mp3player.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.car.mp3player.MusicPlaybackService
import com.car.mp3player.model.Song
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PlaybackBootstrap {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun scanSongs(context: Context, settings: SettingsRepository): List<Song> {
        val songs = MusicScanner(context, settings.scanPaths(), settings.scanTreeUris()).scan()
        if (songs.isNotEmpty()) {
            PlaylistCache.save(context, songs)
        }
        return songs
    }

    fun loadCachedSongs(context: Context): List<Song> = PlaylistCache.load(context)

    fun resumeIfNeeded(context: Context, songs: List<Song>, settings: SettingsRepository) {
        if (!settings.autoResumePlayback || songs.isEmpty()) return
        val path = settings.lastSongPath ?: return
        val index = songs.indexOfFirst { it.path == path }
        if (index < 0) {
            settings.lastSongPath = null
            return
        }
        if (!songReadable(songs[index])) {
            settings.lastSongPath = null
            settings.lastPositionMs = 0L
            return
        }
        ioScope.launch { PlaylistCache.saveQueue(context, songs) }
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_INDEX
            putExtra(MusicPlaybackService.EXTRA_INDEX, index)
            putExtra(MusicPlaybackService.EXTRA_SEEK, settings.lastPositionMs)
        }
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    private fun songReadable(song: Song): Boolean {
        if (song.path.startsWith("content://")) return true
        return runCatching { File(song.path).isFile }.getOrDefault(false)
    }
}
