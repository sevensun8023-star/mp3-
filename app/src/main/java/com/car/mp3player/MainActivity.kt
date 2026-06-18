package com.car.mp3player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.car.mp3player.data.MusicScanner
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.databinding.ActivityMainBinding
import com.car.mp3player.model.Song
import com.car.mp3player.playback.PlaybackStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), PlaybackStateHolder.Listener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsRepository
    private lateinit var adapter: SongAdapter
    private var songs: List<Song> = emptyList()

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { scanMusic() }

    private val overlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateOverlayButton()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsRepository(this)

        adapter = SongAdapter { song, index -> playAt(index) }
        binding.songList.layoutManager = LinearLayoutManager(this)
        binding.songList.adapter = adapter

        binding.btnScan.setOnClickListener { requestPermissionsAndScan() }
        binding.btnPlayPause.setOnClickListener { sendPlaybackAction(MusicPlaybackService.ACTION_TOGGLE) }
        binding.btnNext.setOnClickListener { sendPlaybackAction(MusicPlaybackService.ACTION_NEXT) }
        binding.btnPrev.setOnClickListener { sendPlaybackAction(MusicPlaybackService.ACTION_PREV) }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnOverlay.setOnClickListener { toggleOverlay() }

        updateOverlayButton()
        requestPermissionsAndScan()
    }

    override fun onStart() {
        super.onStart()
        PlaybackStateHolder.addListener(this)
        binding.inAppLyricView.update(PlaybackStateHolder.lyricState)
    }

    override fun onStop() {
        PlaybackStateHolder.removeListener(this)
        super.onStop()
    }

    override fun onPlaybackChanged(
        song: Song?,
        playing: Boolean,
        positionMs: Long,
        lines: List<com.car.mp3player.model.LrcLine>
    ) {
        runOnUiThread {
            binding.btnPlayPause.text = if (playing) getString(R.string.pause) else getString(R.string.play)
            binding.nowPlayingText.text = song?.let { "${it.title} - ${it.artist}" } ?: ""
            binding.inAppLyricView.update(PlaybackStateHolder.lyricState)
        }
    }

    private fun requestPermissionsAndScan() {
        val needed = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            storagePermission.launch(needed.toTypedArray())
        } else {
            scanMusic()
        }
    }

    private fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun scanMusic() {
        CoroutineScope(Dispatchers.Main).launch {
            songs = withContext(Dispatchers.IO) { MusicScanner(this@MainActivity).scan() }
            adapter.submit(songs)
            binding.emptyText.visibility = if (songs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            PlaybackStateHolder.setPlaylist(songs)
            if (songs.isNotEmpty()) {
                Toast.makeText(this@MainActivity, "找到 ${songs.size} 首歌曲", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playAt(index: Int) {
        if (songs.isEmpty()) return
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_INDEX
            putExtra(MusicPlaybackService.EXTRA_INDEX, index)
            putParcelableArrayListExtra(
                MusicPlaybackService.EXTRA_PLAYLIST,
                ArrayList(songs.map { SongParcelable.from(it) })
            )
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendPlaybackAction(action: String) {
        startService(Intent(this, MusicPlaybackService::class.java).apply { this.action = action })
    }

    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermission.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        settings.overlayEnabled = !settings.overlayEnabled
        updateOverlayButton()
        if (settings.overlayEnabled) {
            LyricsOverlayService.start(this)
        } else {
            LyricsOverlayService.stop(this)
        }
    }

    private fun updateOverlayButton() {
        val enabled = settings.overlayEnabled && Settings.canDrawOverlays(this)
        binding.btnOverlay.text = if (enabled) getString(R.string.overlay_on) else getString(R.string.overlay_off)
    }
}
