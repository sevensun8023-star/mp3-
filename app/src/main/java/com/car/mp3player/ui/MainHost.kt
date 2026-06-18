package com.car.mp3player.ui

import com.car.mp3player.model.Song

interface MainHost {
    fun playSongAt(index: Int)
    fun playSongSubset(subset: List<Song>, index: Int)
    fun switchToTab(index: Int)
    fun scanMusic(onDone: ((Int) -> Unit)? = null)
    fun allSongs(): List<Song>
    fun notifyLyricStyleChanged()
}
