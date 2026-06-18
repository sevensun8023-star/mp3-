package com.car.mp3player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.car.mp3player.data.PlaybackBootstrap
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.playback.PlaybackStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BootResumeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settings by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            val cached = withContext(Dispatchers.IO) {
                PlaybackBootstrap.loadCachedSongs(this@BootResumeService)
            }
            if (cached.isNotEmpty()) {
                PlaybackStateHolder.setPlaylist(cached)
                PlaybackBootstrap.resumeIfNeeded(this@BootResumeService, cached, settings)
            }

            launchMainActivityIfNeeded()

            withContext(Dispatchers.IO) {
                val scanned = PlaybackBootstrap.scanSongs(this@BootResumeService, settings)
                if (scanned.isNotEmpty()) {
                    PlaybackStateHolder.setPlaylist(scanned)
                    if (!PlaybackStateHolder.isPlaying) {
                        PlaybackBootstrap.resumeIfNeeded(this@BootResumeService, scanned, settings)
                    }
                }
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun launchMainActivityIfNeeded() {
        if (!settings.bootAutoStart) return
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(launchIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_boot),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.boot_starting))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "boot_resume"
        private const val NOTIFICATION_ID = 1002
    }
}
