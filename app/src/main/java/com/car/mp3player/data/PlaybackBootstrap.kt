package com.car.mp3player.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.car.mp3player.MusicPlaybackService
import com.car.mp3player.SongParcelable
import com.car.mp3player.model.Song

object PlaybackBootstrap {
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
        if (index < 0) return
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_INDEX
            putExtra(MusicPlaybackService.EXTRA_INDEX, index)
            putExtra(MusicPlaybackService.EXTRA_SEEK, settings.lastPositionMs)
            putParcelableArrayListExtra(
                MusicPlaybackService.EXTRA_PLAYLIST,
                ArrayList(songs.map { SongParcelable.from(it) })
            )
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
