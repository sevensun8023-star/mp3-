package com.car.mp3player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.car.mp3player.data.MusicScanner
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.databinding.ActivityMainBinding
import com.car.mp3player.model.Song
import com.car.mp3player.playback.PlaybackStateHolder
import com.car.mp3player.ui.MainHost
import com.car.mp3player.ui.MainPagerAdapter
import com.car.mp3player.ui.PlaylistFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), MainHost {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsRepository
    private var songs: List<Song> = emptyList()

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { scanMusic() }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = SettingsRepository(this)
        settings.applyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = MainPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.offscreenPageLimit = 3

        binding.bottomNav.setOnItemSelectedListener { item ->
            binding.viewPager.setCurrentItem(
                when (item.itemId) {
                    R.id.nav_playlist -> 0
                    R.id.nav_player -> 1
                    else -> 2
                },
                false
            )
            true
        }

        requestPermissionsAndScan()
    }

    override fun playSongAt(index: Int) {
        if (songs.isEmpty() || index !in songs.indices) return
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

    override fun switchToTab(index: Int) {
        binding.viewPager.setCurrentItem(index, false)
        binding.bottomNav.menu.getItem(index).isChecked = true
    }

    override fun scanMusic(onDone: ((Int) -> Unit)?) {
        CoroutineScope(Dispatchers.Main).launch {
            songs = withContext(Dispatchers.IO) {
                MusicScanner(this@MainActivity, settings.scanPaths()).scan()
            }
            PlaybackStateHolder.setPlaylist(songs)
            (supportFragmentManager.findFragmentByTag("f0") as? PlaylistFragment)?.refreshFromHost()
            if (settings.autoResumePlayback) {
                val path = settings.lastSongPath
                val index = if (path != null) songs.indexOfFirst { it.path == path } else -1
                if (index >= 0) {
                    val intent = Intent(this@MainActivity, MusicPlaybackService::class.java).apply {
                        action = MusicPlaybackService.ACTION_PLAY_INDEX
                        putExtra(MusicPlaybackService.EXTRA_INDEX, index)
                        putExtra(MusicPlaybackService.EXTRA_SEEK, settings.lastPositionMs)
                        putParcelableArrayListExtra(
                            MusicPlaybackService.EXTRA_PLAYLIST,
                            ArrayList(songs.map { SongParcelable.from(it) })
                        )
                    }
                    ContextCompat.startForegroundService(this@MainActivity, intent)
                }
            }
            onDone?.invoke(songs.size)
        }
    }

    override fun allSongs(): List<Song> = songs

    private fun requestPermissionsAndScan() {
        val needed = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            storagePermission.launch(needed.toTypedArray())
        } else {
            scanMusic {
                if (it > 0) {
                    Toast.makeText(this, getString(R.string.scan_done, it), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
