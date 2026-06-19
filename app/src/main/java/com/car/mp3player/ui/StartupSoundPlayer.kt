package com.car.mp3player.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.car.mp3player.R
import com.car.mp3player.data.SettingsRepository

object StartupSoundPlayer {
    private var playedThisSession = false

    fun playIfNeeded(context: Context, settings: SettingsRepository) {
        if (!settings.startupSoundEnabled || playedThisSession) return
        playedThisSession = true
        runCatching {
            MediaPlayer.create(context.applicationContext, R.raw.hello_linjun)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnCompletionListener { release() }
                setOnErrorListener { _, _, _ ->
                    release()
                    true
                }
                start()
            }
        }
    }
}
