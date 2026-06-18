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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.car.mp3player.lrc.LrcParser
import com.car.mp3player.model.Song
import com.car.mp3player.playback.PlaybackStateHolder
import java.io.File

class MusicPlaybackService : Service(), PlaybackStateHolder.Listener {
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var playlist: List<Song> = emptyList()
    private var currentIndex = -1
    private var currentLines = emptyList<com.car.mp3player.model.LrcLine>()

    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            PlaybackStateHolder.update(
                song = playlist.getOrNull(currentIndex),
                playing = p.isPlaying,
                positionMs = p.currentPosition,
                lines = currentLines
            )
            LyricsOverlayService.updateLyrics(applicationContext, PlaybackStateHolder.lyricState)
            handler.postDelayed(this, 120L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        playNext()
                    }
                }
            })
        }
        PlaybackStateHolder.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_INDEX -> {
                val index = intent.getIntExtra(EXTRA_INDEX, 0)
                val list = readPlaylist(intent)?.map { it.toSong() } ?: playlist
                startPlaylist(list, index)
            }
            ACTION_TOGGLE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrevious()
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startPlaylist(list: List<Song>, index: Int) {
        playlist = list
        currentIndex = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        PlaybackStateHolder.setPlaylist(list, currentIndex)
        playSongAt(currentIndex)
    }

    private fun playSongAt(index: Int) {
        if (index !in playlist.indices) return
        currentIndex = index
        val song = playlist[index]
        currentLines = song.lrcPath?.let { path ->
            runCatching { LrcParser.parseFile(File(path)) }.getOrDefault(emptyList())
        } ?: emptyList()

        player?.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(song.path))))
        player?.prepare()
        player?.play()
        startForeground(NOTIFICATION_ID, buildNotification(song.title, true))
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
        PlaybackStateHolder.update(song, true, 0L, currentLines)
        LyricsOverlayService.start(applicationContext)
        LyricsOverlayService.updateLyrics(applicationContext, PlaybackStateHolder.lyricState)
    }

    private fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
        val song = playlist.getOrNull(currentIndex)
        updateNotification(song?.title ?: "MP3播放器", p.isPlaying)
        PlaybackStateHolder.update(song, p.isPlaying, p.currentPosition, currentLines)
    }

    private fun playNext() {
        if (playlist.isEmpty()) return
        val next = if (currentIndex + 1 < playlist.size) currentIndex + 1 else 0
        playSongAt(next)
    }

    private fun playPrevious() {
        if (playlist.isEmpty()) return
        val p = player ?: return
        if (p.currentPosition > 3000) {
            p.seekTo(0)
            return
        }
        val prev = if (currentIndex - 1 >= 0) currentIndex - 1 else playlist.lastIndex
        playSongAt(prev)
    }

    override fun onPlaybackChanged(
        song: Song?,
        playing: Boolean,
        positionMs: Long,
        lines: List<com.car.mp3player.model.LrcLine>
    ) {
        // no-op; service is source of truth
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressRunnable)
        PlaybackStateHolder.removeListener(this)
        player?.release()
        player = null
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

    private fun buildNotification(title: String, playing: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(R.string.now_playing))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, playing: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(title, playing))
    }

    companion object {
        const val ACTION_PLAY_INDEX = "play_index"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_NEXT = "next"
        const val ACTION_PREV = "prev"
        const val ACTION_STOP = "stop"
        const val EXTRA_INDEX = "index"
        const val EXTRA_PLAYLIST = "playlist"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001
    }
}
