package com.car.mp3player.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.car.mp3player.MusicPlaybackService
import com.car.mp3player.model.LibraryKind
import com.car.mp3player.model.Song
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PlaybackBootstrap {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun scanMusicLibrary(context: Context, settings: SettingsRepository): List<Song> {
        val songs = MusicScanner(
            context,
            settings.scanPaths(),
            settings.scanTreeUris(),
            excludePaths = settings.podcastPaths()
        ).scan()
        if (songs.isNotEmpty()) {
            PlaylistCache.save(context, songs)
        }
        return songs
    }

    fun scanPodcastLibrary(context: Context, settings: SettingsRepository): List<Song> {
        PodcastPaths.ensureDefaultFolder()
        val paths = settings.podcastPaths()
        if (paths.isEmpty()) return emptyList()
        val songs = MusicScanner(
            context,
            customPaths = paths,
            folderArtist = PodcastPaths.DEFAULT_ARTIST
        ).scanFoldersOnly()
        if (songs.isNotEmpty()) {
            PlaylistCache.savePodcast(context, songs)
        }
        return songs
    }

    fun scanAllLibraries(context: Context, settings: SettingsRepository): ScanResult {
        val music = scanMusicLibrary(context, settings)
        val podcast = scanPodcastLibrary(context, settings)
        return ScanResult(music, podcast)
    }

    /** @deprecated use scanMusicLibrary */
    fun scanSongs(context: Context, settings: SettingsRepository): List<Song> =
        scanMusicLibrary(context, settings)

    fun loadCachedMusic(context: Context): List<Song> = PlaylistCache.load(context)

    fun loadCachedPodcast(context: Context): List<Song> = PlaylistCache.loadPodcast(context)

    fun loadCachedSongs(context: Context): List<Song> = loadCachedMusic(context)

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
        val library = settings.inferLibrary(path)
        ioScope.launch { PlaylistCache.saveQueue(context, songs, library) }
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_INDEX
            putExtra(MusicPlaybackService.EXTRA_INDEX, index)
            putExtra(MusicPlaybackService.EXTRA_SEEK, settings.lastPositionMs)
            putExtra(MusicPlaybackService.EXTRA_LIBRARY, library.name)
        }
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    private fun songReadable(song: Song): Boolean {
        if (song.path.startsWith("content://")) return true
        return runCatching { File(song.path).isFile }.getOrDefault(false)
    }

    data class ScanResult(val music: List<Song>, val podcast: List<Song>)
}
