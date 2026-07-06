package com.car.mp3player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.playback.PlaybackStateHolder

/**
 * 尽量优先接管方向盘 / 蓝牙媒体键，避免切到车机自带播放器。
 */
class CarMediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MEDIA_BUTTON) return
        if (!shouldHandle(context)) return

        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        } ?: return

        if (keyEvent.action != KeyEvent.ACTION_DOWN) return

        val action = when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> MusicPlaybackService.ACTION_NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MusicPlaybackService.ACTION_PREV
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> MusicPlaybackService.ACTION_TOGGLE
            else -> return
        }

        ContextCompat.startForegroundService(
            context,
            Intent(context, MusicPlaybackService::class.java).apply { this.action = action }
        )
        if (isOrderedBroadcast) {
            abortBroadcast()
        }
    }

    private fun shouldHandle(context: Context): Boolean {
        if (PlaybackStateHolder.isPlaying) return true
        return SettingsRepository(context).lastSongPath != null
    }
}
