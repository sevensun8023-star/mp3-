package com.car.mp3player.playback

import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.LyricState
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
    var lrcLines: List<LrcLine> = emptyList()
        private set

    val currentSong: Song?
        get() = songs.getOrNull(currentIndex)

    val lyricState: LyricState
        get() = com.car.mp3player.lrc.LrcParser.findState(lrcLines, positionMs)

    interface Listener {
        fun onPlaybackChanged(song: Song?, playing: Boolean, positionMs: Long, lines: List<LrcLine>)
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun setPlaylist(list: List<Song>, startIndex: Int = 0) {
        songs = list
        currentIndex = startIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        notify()
    }

    fun update(song: Song?, playing: Boolean, positionMs: Long, lines: List<LrcLine>) {
        isPlaying = playing
        this.positionMs = positionMs
        lrcLines = lines
        if (song != null) {
            val idx = songs.indexOfFirst { it.path == song.path }
            if (idx >= 0) currentIndex = idx
        }
        notify(song)
    }

    fun moveTo(index: Int) {
        if (index in songs.indices) {
            currentIndex = index
        }
    }

    private fun notify(song: Song? = currentSong) {
        listeners.forEach { it.onPlaybackChanged(song, isPlaying, positionMs, lrcLines) }
    }
}
