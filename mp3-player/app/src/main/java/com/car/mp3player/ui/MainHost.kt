package com.car.mp3player.ui

interface MainHost {
    fun playSongAt(index: Int)
    fun switchToTab(index: Int)
    fun scanMusic(onDone: ((Int) -> Unit)? = null)
    fun allSongs(): List<com.car.mp3player.model.Song>
}
