package com.car.mp3player.playback

import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.LyricState
import com.car.mp3player.model.PlaybackMode
import com.car.mp3player.model.Song
import java.util.concurrent.CopyOnWriteArrayList

object PlaybackStateHolder {
    private val listeners = CopyOnWriteArrayList<Listener>()

    var songs: List<Song> = emptyList()
        private set
    var currentIndex: Int = -1
        private set
    var isPlaying: Boolean = false
        private set
    var positionMs: Long = 0L
        private set
    var durationMs: Long = 0L
        private set
    var lrcLines: List<LrcLine> = emptyList()
        private set
    var coverArtPath: String? = null
        private set
    var playMode: PlaybackMode = PlaybackMode.ORDER
        private set

    val currentSong: Song?
        get() = songs.getOrNull(currentIndex)

    val lyricState: LyricState
        get() = com.car.mp3player.lrc.LrcParser.findState(lrcLines, positionMs)

    interface Listener {
        fun onPlaybackChanged(song: Song?, playing: Boolean, positionMs: Long, lines: List<LrcLine>)
        fun onPlayModeChanged(mode: PlaybackMode) {}
        fun onPlaylistChanged(songs: List<Song>) {}
        fun onCoverChanged(coverPath: String?) {}
        fun onDurationChanged(durationMs: Long) {}
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        listener.onPlayModeChanged(playMode)
        listener.onPlaylistChanged(songs)
        listener.onCoverChanged(coverArtPath)
        listener.onDurationChanged(durationMs)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun setPlaylist(list: List<Song>, startIndex: Int = 0) {
        songs = list
        currentIndex = startIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        listeners.forEach { it.onPlaylistChanged(list) }
        notify()
    }

    fun setPlayMode(mode: PlaybackMode) {
        playMode = mode
        listeners.forEach { it.onPlayModeChanged(mode) }
    }

    fun setCoverArt(path: String?) {
        coverArtPath = path
        listeners.forEach { it.onCoverChanged(path) }
    }

    fun setDuration(durationMs: Long) {
        if (this.durationMs == durationMs) return
        this.durationMs = durationMs
        listeners.forEach { it.onDurationChanged(durationMs) }
    }

    fun update(song: Song?, playing: Boolean, positionMs: Long, lines: List<LrcLine>, durationMs: Long = this.durationMs) {
        isPlaying = playing
        this.positionMs = positionMs
        lrcLines = lines
        if (durationMs > 0) this.durationMs = durationMs
        if (song != null) {
            val idx = songs.indexOfFirst { it.path == song.path }
            if (idx >= 0) currentIndex = idx
        }
        notify(song)
    }

    private fun notify(song: Song? = currentSong) {
        listeners.forEach { it.onPlaybackChanged(song, isPlaying, positionMs, lrcLines) }
    }
}
