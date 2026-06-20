package com.car.mp3player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
    private var lastClusterMetadataKey = ""
    private val metadataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val metadataLoader by lazy {
        SongMetadataLoader(cacheDir, settings, coverFetcher = com.car.mp3player.data.CoverArtFetcher(this))
    }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focus ->
        val player = exoPlayer ?: return@OnAudioFocusChangeListener
        when (focus) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (!player.isPlaying && playlist.isNotEmpty()) player.play()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                player.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                player.pause()
            }
        }
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = exoPlayer ?: return
            val song = playlist.getOrNull(currentIndex)
            PlaybackStateHolder.update(song, p.isPlaying, p.currentPosition, currentLines, p.duration.coerceAtLeast(0L))
            LyricsOverlayService.updateLyrics(applicationContext, PlaybackStateHolder.lyricState)
            updateClusterMetadata(song, p.currentPosition)
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

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val idx = currentMediaItemIndex
                    if (idx in playlist.indices && idx != currentIndex) {
                        applyTrackChange(idx)
                    }
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
            ACTION_RELOAD_LYRICS -> reloadLyrics(intent)
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
        acquireAudioFocus()
        currentIndex = index
        val song = playlist[index]
        val player = exoPlayer ?: return

        if (isQueueSynced(player)) {
            player.seekTo(index, seekMs)
        } else {
            player.setMediaItems(buildMediaItems(), index, seekMs)
            player.prepare()
        }
        player.play()

        lastClusterMetadataKey = ""
        PlaybackStateHolder.setCoverArt(null)
        currentLines = loadLocalLyrics(song)
        startForeground(NOTIFICATION_ID, buildNotification(song))
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
        persistProgress(song, seekMs)
        PlaybackStateHolder.update(song, true, seekMs, currentLines, player.duration.coerceAtLeast(0L))
        updateClusterMetadata(song, seekMs)
        LyricsOverlayService.start(applicationContext)
        LyricsOverlayService.updateLyrics(applicationContext, PlaybackStateHolder.lyricState)
        ClusterLyricService.start(applicationContext)
        loadMetadataAsync(song)
    }

    private fun applyTrackChange(index: Int) {
        if (index !in playlist.indices) return
        currentIndex = index
        val song = playlist[index]
        lastClusterMetadataKey = ""
        PlaybackStateHolder.setCoverArt(null)
        currentLines = loadLocalLyrics(song)
        val player = exoPlayer
        val pos = player?.currentPosition ?: 0L
        persistProgress(song, pos)
        PlaybackStateHolder.update(
            song,
            player?.isPlaying == true,
            pos,
            currentLines,
            player?.duration?.coerceAtLeast(0L) ?: PlaybackStateHolder.durationMs
        )
        updateClusterMetadata(song, pos)
        updateNotification(song)
        LyricsOverlayService.updateLyrics(applicationContext, PlaybackStateHolder.lyricState)
        loadMetadataAsync(song)
    }

    private fun buildMediaItems(): List<MediaItem> {
        return playlist.map { song ->
            MediaItem.Builder()
                .setUri(songUri(song))
                .setMediaId(song.path)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .build()
                )
                .build()
        }
    }

    private fun isQueueSynced(player: ExoPlayer): Boolean {
        if (player.mediaItemCount != playlist.size || playlist.isEmpty()) return false
        for (i in playlist.indices) {
            if (player.getMediaItemAt(i).mediaId != playlist[i].path) return false
        }
        return true
    }

    private fun acquireAudioFocus() {
        if (hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            hasAudioFocus = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            hasAudioFocus = audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun releaseAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
        hasAudioFocus = false
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
        acquireAudioFocus()
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

    private fun reloadLyrics(intent: Intent) {
        val song = playlist.getOrNull(currentIndex) ?: return
        val title = intent.getStringExtra(EXTRA_SEARCH_TITLE)?.trim().orEmpty()
        val artist = intent.getStringExtra(EXTRA_SEARCH_ARTIST)?.trim().orEmpty()
        if (title.isNotEmpty()) {
            settings.setLyricSearchOverride(song.path, title, artist.ifBlank { song.artist })
        }
        metadataScope.launch {
            val lines = metadataLoader.loadLyrics(song, forceOnline = true) ?: return@launch
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

    private fun updateClusterMetadata(song: Song?, positionMs: Long) {
        if (song == null) return
        val lyricLine = LrcParser.findState(currentLines, positionMs).currentLine?.text
        val subtitle = lyricLine?.takeIf { it.isNotBlank() } ?: song.artist
        val key = "${song.path}|$subtitle"
        if (key == lastClusterMetadataKey) return
        lastClusterMetadataKey = key
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.artist)
            .setDisplayTitle(song.title)
            .setSubtitle(subtitle)
            .setDescription(lyricLine ?: song.title)
            .build()
        val player = exoPlayer ?: return
        val index = player.currentMediaItemIndex
        if (index < 0) return
        val current = player.currentMediaItem ?: return
        player.replaceMediaItem(
            index,
            current.buildUpon().setMediaMetadata(metadata).build()
        )
        updateNotification(song, subtitle)
    }

    private fun persistProgress(song: Song?, positionMs: Long) {
        if (song == null) return
        settings.lastSongPath = song.path
        settings.lastPositionMs = positionMs
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressRunnable)
        releaseAudioFocus()
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

    private fun buildNotification(song: Song?, subtitle: String? = null): Notification {
        val session = mediaSession
        val title = song?.title ?: getString(R.string.app_name)
        val line = subtitle?.takeIf { it.isNotBlank() }
        val text = line ?: song?.artist?.takeIf { it.isNotBlank() } ?: getString(R.string.now_playing)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
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

    private fun updateNotification(song: Song?, subtitle: String? = null) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(song, subtitle))
    }

    companion object {
        const val ACTION_PLAY_INDEX = "play_index"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_NEXT = "next"
        const val ACTION_PREV = "prev"
        const val ACTION_SET_MODE = "set_mode"
        const val ACTION_RESUME = "resume"
        const val ACTION_SEEK = "seek"
        const val ACTION_RELOAD_LYRICS = "reload_lyrics"
        const val ACTION_STOP = "stop"
        const val EXTRA_INDEX = "index"
        const val EXTRA_SEEK = "seek"
        const val EXTRA_PLAYLIST = "playlist"
        const val EXTRA_MODE = "mode"
        const val EXTRA_SEARCH_TITLE = "search_title"
        const val EXTRA_SEARCH_ARTIST = "search_artist"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001
    }
}
