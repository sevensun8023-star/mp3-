package com.car.mp3player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.data.SongMetadataLoader
import com.car.mp3player.lrc.LrcParser
import com.car.mp3player.model.PlaybackMode
import com.car.mp3player.model.Song
import com.car.mp3player.playback.CarPlaylistPlayer
import com.car.mp3player.playback.PlaybackStateHolder
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlaybackService : Service() {
    private var exoPlayer: ExoPlayer? = null
    private var sessionPlayer: CarPlaylistPlayer? = null
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val settings by lazy { SettingsRepository(this) }
    private var playlist: List<Song> = emptyList()
    private var currentIndex = -1
    private var currentLines = emptyList<com.car.mp3player.model.LrcLine>()
    private var shuffleQueue = mutableListOf<Int>()
    private var saveTick = 0
    private val metadataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val metadataLoader by lazy {
        SongMetadataLoader(cacheDir, settings, coverFetcher = com.car.mp3player.data.CoverArtFetcher(this))
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = exoPlayer ?: return
            val song = playlist.getOrNull(currentIndex)
            PlaybackStateHolder.update(song, p.isPlaying, p.currentPosition, currentLines, p.duration.coerceAtLeast(0L))
            LyricsOverlayService.updateLyrics(applicationContext, PlaybackStateHolder.lyricState)
            if (++saveTick % 25 == 0) {
                persistProgress(song, p.currentPosition)
            }
            handler.postDelayed(this, 120L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) playNext()
                }
            })
        }
        exoPlayer = player
        sessionPlayer = CarPlaylistPlayer(
            player = player,
            onSkipNext = { playNext() },
            onSkipPrevious = { playPrevious() },
            hasPlaylist = { playlist.isNotEmpty() }
        )
        mediaSession = MediaSession.Builder(this, sessionPlayer!!).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_INDEX -> {
                val index = intent.getIntExtra(EXTRA_INDEX, 0)
                val seek = intent.getLongExtra(EXTRA_SEEK, 0L)
                val list = readPlaylist(intent)?.map { it.toSong() }
                if (list != null) {
                    startPlaylist(list, index, seek)
                } else if (playlist.isNotEmpty()) {
                    playSongAt(index.coerceIn(0, playlist.lastIndex), seek)
                }
            }
            ACTION_TOGGLE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrevious()
            ACTION_SET_MODE -> {
                val mode = PlaybackMode.entries[intent.getIntExtra(EXTRA_MODE, 0)]
                setPlayMode(mode)
            }
            ACTION_RESUME -> resumeLast()
            ACTION_SEEK -> {
                val seek = intent.getLongExtra(EXTRA_SEEK, 0L)
                exoPlayer?.seekTo(seek.coerceAtLeast(0L))
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun resumeLast() {
        if (playlist.isEmpty()) return
        val path = settings.lastSongPath ?: return
        val index = playlist.indexOfFirst { it.path == path }
        if (index >= 0) {
            playSongAt(index, settings.lastPositionMs.coerceAtLeast(0L))
        }
    }

    private fun setPlayMode(mode: PlaybackMode) {
        PlaybackStateHolder.setPlayMode(mode)
        refillShuffleQueue()
    }

    private fun startPlaylist(list: List<Song>, index: Int, seekMs: Long = 0L) {
        playlist = list
        currentIndex = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        PlaybackStateHolder.setPlaylist(list, currentIndex)
        refillShuffleQueue()
        playSongAt(currentIndex, seekMs)
    }

    private fun playSongAt(index: Int, seekMs: Long = 0L) {
        if (index !in playlist.indices) return
        currentIndex = index
        val song = playlist[index]
        PlaybackStateHolder.setCoverArt(null)
        currentLines = loadLocalLyrics(song)

        val mediaItem = MediaItem.Builder()
            .setUri(songUri(song))
            .setMediaId(song.path)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .build()
            )
            .build()

        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        if (seekMs > 0L) exoPlayer?.seekTo(seekMs)
        exoPlayer?.play()
        startForeground(NOTIFICATION_ID, buildNotification(song))
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
        persistProgress(song, seekMs)
        PlaybackStateHolder.update(song, true, seekMs, currentLines, exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L)
        LyricsOverlayService.start(applicationContext)
        LyricsOverlayService.updateLyrics(applicationContext, PlaybackStateHolder.lyricState)
        loadMetadataAsync(song)
    }

    private fun loadLocalLyrics(song: Song): List<com.car.mp3player.model.LrcLine> {
        song.lrcPath?.let { path ->
            runCatching { LrcParser.parseFile(File(path)) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                return it
            }
        }
        return emptyList()
    }

    private fun loadMetadataAsync(song: Song) {
        metadataScope.launch {
            val cover = metadataLoader.loadCover(song)
            if (playlist.getOrNull(currentIndex)?.path == song.path && cover != null) {
                withContext(Dispatchers.Main) {
                    PlaybackStateHolder.setCoverArt(cover)
                }
            }

            if (loadLocalLyrics(song).isEmpty()) {
                val lines = metadataLoader.loadLyrics(song) ?: return@launch
                if (playlist.getOrNull(currentIndex)?.path != song.path) return@launch
                currentLines = lines
                withContext(Dispatchers.Main) {
                    val p = exoPlayer
                    PlaybackStateHolder.update(
                        song,
                        p?.isPlaying == true,
                        p?.currentPosition ?: 0L,
                        currentLines,
                        p?.duration?.coerceAtLeast(0L) ?: PlaybackStateHolder.durationMs
                    )
                    LyricsOverlayService.updateLyrics(applicationContext, PlaybackStateHolder.lyricState)
                }
            }
        }
    }

    private fun songUri(song: Song): Uri {
        return if (song.path.startsWith("content://")) Uri.parse(song.path) else Uri.fromFile(File(song.path))
    }

    private fun togglePlayPause() {
        val p = exoPlayer ?: return
        if (p.isPlaying) p.pause() else p.play()
        val song = playlist.getOrNull(currentIndex)
        updateNotification(song)
        PlaybackStateHolder.update(song, p.isPlaying, p.currentPosition, currentLines, p.duration.coerceAtLeast(0L))
        persistProgress(song, p.currentPosition)
    }

    private fun playNext() {
        if (playlist.isEmpty()) return
        val next = when (PlaybackStateHolder.playMode) {
            PlaybackMode.SHUFFLE -> {
                if (shuffleQueue.isEmpty()) refillShuffleQueue()
                shuffleQueue.removeFirstOrNull() ?: Random.nextInt(playlist.size)
            }
            PlaybackMode.ORDER -> if (currentIndex + 1 < playlist.size) currentIndex + 1 else 0
        }
        playSongAt(next)
    }

    private fun playPrevious() {
        if (playlist.isEmpty()) return
        val p = exoPlayer ?: return
        if (p.currentPosition > 3000) {
            p.seekTo(0)
            return
        }
        val prev = when (PlaybackStateHolder.playMode) {
            PlaybackMode.SHUFFLE -> {
                if (shuffleQueue.isEmpty()) refillShuffleQueue()
                shuffleQueue.removeLastOrNull() ?: Random.nextInt(playlist.size)
            }
            PlaybackMode.ORDER -> if (currentIndex - 1 >= 0) currentIndex - 1 else playlist.lastIndex
        }
        playSongAt(prev)
    }

    private fun refillShuffleQueue() {
        shuffleQueue = playlist.indices.filter { it != currentIndex }.shuffled().toMutableList()
    }

    private fun persistProgress(song: Song?, positionMs: Long) {
        if (song == null) return
        settings.lastSongPath = song.path
        settings.lastPositionMs = positionMs
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressRunnable)
        mediaSession?.release()
        mediaSession = null
        sessionPlayer = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    private fun readPlaylist(intent: Intent): ArrayList<SongParcelable>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_PLAYLIST, SongParcelable::class.java)
        } else {
            intent.getParcelableArrayListExtra(EXTRA_PLAYLIST)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(song: Song?): Notification {
        val session = mediaSession
        val title = song?.title ?: getString(R.string.app_name)
        val subtitle = song?.artist?.takeIf { it.isNotBlank() } ?: getString(R.string.now_playing)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (session != null) {
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(session))
        }
        return builder.build()
    }

    private fun updateNotification(song: Song?) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(song))
    }

    companion object {
        const val ACTION_PLAY_INDEX = "play_index"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_NEXT = "next"
        const val ACTION_PREV = "prev"
        const val ACTION_SET_MODE = "set_mode"
        const val ACTION_RESUME = "resume"
        const val ACTION_SEEK = "seek"
        const val ACTION_STOP = "stop"
        const val EXTRA_INDEX = "index"
        const val EXTRA_SEEK = "seek"
        const val EXTRA_PLAYLIST = "playlist"
        const val EXTRA_MODE = "mode"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001
    }
}
