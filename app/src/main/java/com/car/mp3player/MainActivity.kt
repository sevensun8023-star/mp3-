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
import androidx.lifecycle.lifecycleScope
import com.car.mp3player.data.PlaybackBootstrap
import com.car.mp3player.data.PlaylistCache
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.databinding.ActivityMainBinding
import com.car.mp3player.model.Song
import com.car.mp3player.playback.PlaybackStateHolder
import com.car.mp3player.ui.AppThemeManager
import com.car.mp3player.ui.MainHost
import com.car.mp3player.ui.MainPagerAdapter
import com.car.mp3player.ui.PlayerFragment
import com.car.mp3player.ui.PlaylistFragment
import com.car.mp3player.ui.StartupSoundPlayer
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
        setTheme(settings.appTheme().styleRes)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = MainPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.offscreenPageLimit = 3
        applyAppTheme()

        binding.bottomNav.selectedItemId = R.id.nav_player
        binding.viewPager.setCurrentItem(1, false)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val index = when (item.itemId) {
                R.id.nav_playlist -> 0
                R.id.nav_player -> 1
                else -> 2
            }
            binding.viewPager.setCurrentItem(index, false)
            if (item.itemId != R.id.nav_player) {
                applyAppTheme()
            }
            true
        }

        restoreCachedPlaylist(resumePlayback = false)
        requestPermissionsAndScan()
        lifecycleScope.launch {
            if (!PlaybackStateHolder.isPlaying) {
                StartupSoundPlayer.playBeforeBootPlayback(this@MainActivity, settings)
            }
            restoreCachedPlaylist(resumePlayback = true)
        }
    }

    override fun onResume() {
        super.onResume()
        applyAppTheme()
        if (binding.viewPager.currentItem == 1) {
            (supportFragmentManager.findFragmentByTag("f1") as? PlayerFragment)?.syncBottomNavTheme()
        }
    }

    private fun restoreCachedPlaylist(resumePlayback: Boolean = true) {
        songs = PlaybackBootstrap.loadCachedSongs(this)
        if (songs.isEmpty()) return
        PlaybackStateHolder.setPlaylist(songs)
        binding.root.post {
            (supportFragmentManager.findFragmentByTag("f0") as? PlaylistFragment)?.refreshFromHost()
            if (resumePlayback && !PlaybackStateHolder.isPlaying) {
                PlaybackBootstrap.resumeIfNeeded(this, songs, settings)
            }
        }
    }

    override fun playSongAt(index: Int) {
        playSongSubset(songs, index)
    }

    override fun playSongSubset(subset: List<Song>, index: Int) {
        if (subset.isEmpty() || index !in subset.indices) return
        PlaylistCache.saveQueue(this, subset)
        PlaybackStateHolder.setPlaylist(subset, index)
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_INDEX
            putExtra(MusicPlaybackService.EXTRA_INDEX, index)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun notifyLyricStyleChanged() {
        (supportFragmentManager.findFragmentByTag("f1") as? PlayerFragment)?.refreshLyricStyle()
    }

    override fun switchToTab(index: Int) {
        binding.viewPager.setCurrentItem(index, false)
        binding.bottomNav.menu.getItem(index).isChecked = true
    }

    override fun scanMusic(onDone: ((Int) -> Unit)?) {
        CoroutineScope(Dispatchers.Main).launch {
            val scanned = withContext(Dispatchers.IO) {
                PlaybackBootstrap.scanSongs(this@MainActivity, settings)
            }
            if (scanned.isNotEmpty()) {
                songs = scanned
                PlaybackStateHolder.setPlaylist(songs)
                (supportFragmentManager.findFragmentByTag("f0") as? PlaylistFragment)?.refreshFromHost()
                if (!PlaybackStateHolder.isPlaying) {
                    PlaybackBootstrap.resumeIfNeeded(this@MainActivity, songs, settings)
                }
            }
            onDone?.invoke(songs.size)
        }
    }

    override fun allSongs(): List<Song> = songs

    override fun refreshAppTheme() {
        applyAppTheme()
    }

    override fun syncPlayerBottomNav(backgroundColor: Int) {
        if (binding.viewPager.currentItem != 1) return
        val palette = AppThemeManager.palette(this, settings)
        AppThemeManager.applyPlayerBottomNav(binding.bottomNav, palette, backgroundColor)
        window.navigationBarColor = AppThemeManager.playerBottomNavColors(palette, backgroundColor)
    }

    private fun applyAppTheme() {
        val palette = AppThemeManager.palette(this, settings)
        binding.root.setBackgroundColor(palette.background)
        AppThemeManager.applyBottomNav(binding.bottomNav, palette)
        window.navigationBarColor = palette.bottomNavBg
        window.statusBarColor = palette.background
    }

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
