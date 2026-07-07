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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.car.mp3player.data.PlaylistCache
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.data.SongMetadataLoader
import com.car.mp3player.lrc.LrcParser
import com.car.mp3player.model.PlaybackMode
import com.car.mp3player.model.Song
import com.car.mp3player.playback.PlaybackStateHolder
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlaybackService : Service() {
    private var exoPlayer: ExoPlayer? = null
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
        SongMetadataLoader(
            applicationContext,
            cacheDir,
            settings,
            coverFetcher = com.car.mp3player.data.CoverArtFetcher(this)
        )
    }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var foregroundStarted = false
    private var playlistJob: Job? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focus ->
        val player = exoPlayer ?: return@OnAudioFocusChangeListener
        when (focus) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (!player.isPlaying && playlist.isNotEmpty() && player.mediaItemCount > 0) {
                    runCatching { player.play() }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                runCatching { player.pause() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                runCatching { player.pause() }
            }
        }
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = exoPlayer ?: return
            val song = playlist.getOrNull(currentIndex)
            PlaybackStateHolder.update(song, p.isPlaying, p.currentPosition, currentLines, p.duration.coerceAtLeast(0L))
            runCatching { LyricsOverlayService.updateLyrics(applicationContext, PlaybackStateHolder.lyricState) }
            updateClusterMetadata(song, p.currentPosition)
            if (++saveTick % 25 == 0) {
                persistProgress(song, p.currentPosition)
            }
            if (p.isPlaying && saveTick % 50 == 0) {
                acquireAudioFocus()
            }
            handler.postDelayed(this, 120L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            createChannel()
            PlaybackStateHolder.setPlayMode(settings.playMode)
            exoPlayer = ExoPlayer.Builder(this).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) playNext()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        android.util.Log.e(TAG, "ExoPlayer error", error)
                    }
                })
            }
        }.onFailure {
            android.util.Log.e(TAG, "onCreate failed", it)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return runCatching { handleStartCommand(intent) }.onFailure {
            android.util.Log.e(TAG, "onStartCommand failed", it)
            stopSelfSafely()
        }.getOrDefault(START_NOT_STICKY)
    }

    private fun handleStartCommand(intent: Intent?): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        if (!requiresForeground(action) && playlist.isEmpty() && PlaybackStateHolder.songs.isEmpty()) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        ensureForeground()

        when (action) {
            ACTION_PLAY_INDEX -> {
                val index = intent.getIntExtra(EXTRA_INDEX, 0)
                val seek = intent.getLongExtra(EXTRA_SEEK, 0L)
                runWithPlaylist(intent) { list ->
                    if (list.isNotEmpty()) {
                        startPlaylist(list, index, seek)
                    } else if (playlist.isNotEmpty()) {
                        playSongAt(index.coerceIn(0, playlist.lastIndex), seek)
                    }
                }
            }
            ACTION_TOGGLE -> {
                if (playlist.isEmpty()) {
                    runWithPlaylist(null) { list ->
                        if (list.isEmpty()) return@runWithPlaylist
                        startFromCachedQueue(list, resume = true)
                    }
                } else {
                    togglePlayPause()
                }
            }
            ACTION_NEXT -> {
                if (playlist.isEmpty()) {
                    runWithPlaylist(null) { list ->
                        if (list.isEmpty()) return@runWithPlaylist
                        startFromCachedQueue(list, shouldPlayNext = true)
                    }
                } else {
                    playNext()
                }
            }
            ACTION_PREV -> {
                if (playlist.isEmpty()) {
                    runWithPlaylist(null) { list ->
                        if (list.isEmpty()) return@runWithPlaylist
                        startFromCachedQueue(list, shouldPlayPrevious = true)
                    }
                } else {
                    playPrevious()
                }
            }
            ACTION_SET_MODE -> {
                val mode = PlaybackMode.entries[intent.getIntExtra(EXTRA_MODE, 0)]
                setPlayMode(mode)
            }
            ACTION_RESUME -> resumeLast()
            ACTION_SEEK -> {
                val seek = intent.getLongExtra(EXTRA_SEEK, 0L)
                runCatching { exoPlayer?.seekTo(seek.coerceAtLeast(0L)) }
            }
            ACTION_RELOAD_LYRICS -> reloadLyrics(intent)
        }
        return if (playlist.isNotEmpty()) START_STICKY else START_NOT_STICKY
    }

    private fun requiresForeground(action: String?): Boolean {
        return when (action) {
            ACTION_SET_MODE, ACTION_STOP -> false
            else -> true
        }
    }

    private fun runWithPlaylist(intent: Intent?, block: (List<Song>) -> Unit) {
        playlistJob?.cancel()
        playlistJob = metadataScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { resolvePlaylist(intent) }
                withContext(Dispatchers.Main) {
                    runCatching { block(list) }.onFailure {
                        android.util.Log.e(TAG, "playback action failed", it)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "resolvePlaylist failed", e)
            }
        }
    }

    private fun resolvePlaylist(intent: Intent?): List<Song> {
        PlaybackStateHolder.songs.takeIf { it.isNotEmpty() }?.let { return it }
        PlaylistCache.loadQueue(this).takeIf { it.isNotEmpty() }?.let { return it }
        PlaylistCache.load(this).takeIf { it.isNotEmpty() }?.let { return it }
        intent?.let { readPlaylist(it) }?.map { it.toSong() }?.takeIf { it.isNotEmpty() }?.let { return it }
        return emptyList()
    }

    private fun startFromCachedQueue(
        list: List<Song>,
        resume: Boolean = false,
        shouldPlayNext: Boolean = false,
        shouldPlayPrevious: Boolean = false
    ) {
        if (list.isEmpty()) return
        if (playlist.isEmpty()) {
            val path = settings.lastSongPath
            val index = path?.let { p -> list.indexOfFirst { it.path == p } }?.takeIf { it >= 0 } ?: 0
            val seek = if (resume) settings.lastPositionMs.coerceAtLeast(0L) else 0L
            startPlaylist(list, index, seek)
            if (shouldPlayNext) playNext()
            else if (shouldPlayPrevious) playPrevious()
            return
        }
        when {
            shouldPlayNext -> playNext()
            shouldPlayPrevious -> playPrevious()
        }
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        runCatching {
            startForeground(NOTIFICATION_ID, buildNotification(playlist.getOrNull(currentIndex)))
            foregroundStarted = true
        }.onFailure {
            runCatching {
                startForeground(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.now_playing))
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setOngoing(true)
                        .build()
                )
                foregroundStarted = true
            }.onFailure {
                android.util.Log.e(TAG, "ensureForeground failed", it)
            }
        }
    }

    private fun stopSelfSafely() {
        if (foregroundStarted) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            }
            foregroundStarted = false
        }
        stopSelf()
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
        settings.playMode = mode
        refillShuffleQueue()
    }

    private fun startPlaylist(list: List<Song>, index: Int, seekMs: Long = 0L) {
        playlist = list
        currentIndex = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        when {
            PlaybackStateHolder.songs.isEmpty() -> PlaybackStateHolder.setPlaylist(list, currentIndex)
            PlaybackStateHolder.songs === list -> PlaybackStateHolder.setCurrentIndex(currentIndex)
            else -> PlaybackStateHolder.setPlaylist(list, currentIndex)
        }
        refillShuffleQueue()
        playSongAt(currentIndex, seekMs)
    }

    private fun playSongAt(index: Int, seekMs: Long = 0L) {
        if (index !in playlist.indices) return
        val song = playlist[index]
        if (!songFileReadable(song)) {
            android.util.Log.e(TAG, "song not readable: ${song.path}")
            return
        }
        runCatching {
            acquireAudioFocus()
            currentIndex = index
            val player = exoPlayer ?: return@runCatching

            val item = buildMediaItem(song)
            player.setMediaItem(item, seekMs)
            player.prepare()
            player.play()

            lastClusterMetadataKey = ""
            PlaybackStateHolder.setCoverArt(null)
            currentLines = loadLocalLyrics(song)
            ensureForeground()
            updateNotification(song)
            handler.removeCallbacks(progressRunnable)
            handler.post(progressRunnable)
            persistProgress(song, seekMs)
            PlaybackStateHolder.update(song, true, seekMs, currentLines, player.duration.coerceAtLeast(0L))
            updateClusterMetadata(song, seekMs)
            handler.post {
                runCatching { LyricsOverlayService.start(applicationContext) }
                LyricsOverlayService.updateLyrics(applicationContext, PlaybackStateHolder.lyricState)
                runCatching { ClusterLyricService.start(applicationContext) }
            }
            loadMetadataAsync(song)
        }.onFailure {
            android.util.Log.e(TAG, "playSongAt failed", it)
        }
    }

    private fun songFileReadable(song: Song): Boolean {
        if (song.path.startsWith("content://")) return true
        return runCatching { File(song.path).isFile }.getOrDefault(false)
    }

    private fun buildMediaItem(song: Song): MediaItem {
        return MediaItem.Builder()
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
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusListener, handler)
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
        return metadataLoader.readLocalLyrics(song) ?: emptyList()
    }

    private fun persistSongLrcPath(songPath: String, lrcPath: String) {
        val idx = playlist.indexOfFirst { it.path == songPath }
        if (idx < 0) return
        val updated = playlist[idx].copy(lrcPath = lrcPath)
        playlist = playlist.toMutableList().apply { set(idx, updated) }
        PlaybackStateHolder.updateSongLrcPath(songPath, lrcPath)
        metadataScope.launch(Dispatchers.IO) {
            PlaylistCache.saveQueue(applicationContext, playlist)
        }
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
                val result = metadataLoader.loadLyrics(song) ?: return@launch
                if (playlist.getOrNull(currentIndex)?.path != song.path) return@launch
                result.lrcPath?.let { persistSongLrcPath(song.path, it) }
                currentLines = result.lines
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
        runCatching {
            val p = exoPlayer ?: return@runCatching
            if (p.mediaItemCount <= 0) {
                if (playlist.isNotEmpty()) {
                    playSongAt(currentIndex.coerceIn(0, playlist.lastIndex))
                }
                return@runCatching
            }
            if (p.isPlaying) p.pause() else p.play()
            val song = playlist.getOrNull(currentIndex)
            updateNotification(song)
            PlaybackStateHolder.update(song, p.isPlaying, p.currentPosition, currentLines, p.duration.coerceAtLeast(0L))
            persistProgress(song, p.currentPosition)
        }.onFailure {
            android.util.Log.e(TAG, "togglePlayPause failed", it)
        }
    }

    private fun playNext() {
        runCatching {
            if (playlist.isEmpty()) return@runCatching
            val next = when (PlaybackStateHolder.playMode) {
                PlaybackMode.SHUFFLE -> {
                    if (shuffleQueue.isEmpty()) refillShuffleQueue()
                    shuffleQueue.removeFirstOrNull() ?: Random.nextInt(playlist.size)
                }
                PlaybackMode.ORDER -> if (currentIndex + 1 < playlist.size) currentIndex + 1 else 0
            }
            playSongAt(next)
        }.onFailure {
            android.util.Log.e(TAG, "playNext failed", it)
        }
    }

    private fun playPrevious() {
        runCatching {
            if (playlist.isEmpty()) return@runCatching
            val p = exoPlayer ?: return@runCatching
            if (p.mediaItemCount > 0 && p.currentPosition > 3000) {
                p.seekTo(0)
                return@runCatching
            }
            val prev = when (PlaybackStateHolder.playMode) {
                PlaybackMode.SHUFFLE -> {
                    if (shuffleQueue.isEmpty()) refillShuffleQueue()
                    shuffleQueue.removeLastOrNull() ?: Random.nextInt(playlist.size)
                }
                PlaybackMode.ORDER -> if (currentIndex - 1 >= 0) currentIndex - 1 else playlist.lastIndex
            }
            playSongAt(prev)
        }.onFailure {
            android.util.Log.e(TAG, "playPrevious failed", it)
        }
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
            val result = metadataLoader.loadLyrics(song, forceOnline = true) ?: return@launch
            if (playlist.getOrNull(currentIndex)?.path != song.path) return@launch
            result.lrcPath?.let { persistSongLrcPath(song.path, it) }
            currentLines = result.lines
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
        if (player.mediaItemCount <= 0) return
        val current = player.currentMediaItem ?: return
        runCatching {
            player.replaceMediaItem(
                player.currentMediaItemIndex.coerceAtLeast(0),
                current.buildUpon().setMediaMetadata(metadata).build()
            )
        }
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
        runCatching { exoPlayer?.release() }
        exoPlayer = null
        foregroundStarted = false
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
        val title = song?.title ?: getString(R.string.app_name)
        val line = subtitle?.takeIf { it.isNotBlank() }
        val text = line ?: song?.artist?.takeIf { it.isNotBlank() } ?: getString(R.string.now_playing)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(song: Song?, subtitle: String? = null) {
        if (!foregroundStarted) return
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(song, subtitle))
        }
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
        private const val TAG = "MusicPlaybackService"
    }
}
