package com.car.mp3player.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.car.mp3player.R
import com.car.mp3player.data.SettingsRepository
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object StartupSoundPlayer {
    @Volatile
    private var greetingPlayedThisSession = false

    /** 开机续播前播放问候音（阻塞直到播完） */
    suspend fun playBeforeBootPlayback(context: Context, settings: SettingsRepository) {
        if (!settings.startupSoundEnabled || greetingPlayedThisSession) return
        greetingPlayedThisSession = true
        suspendCancellableCoroutine { cont ->
            playInternal(context.applicationContext) { cont.resume(Unit) }
        }
    }

    /** 手动打开 App 时播放（若开机已播过则跳过） */
    fun playIfNeeded(context: Context, settings: SettingsRepository) {
        if (!settings.startupSoundEnabled || greetingPlayedThisSession) return
        greetingPlayedThisSession = true
        playInternal(context.applicationContext) {}
    }

    private fun playInternal(context: Context, onDone: () -> Unit) {
        runCatching {
            MediaPlayer.create(context, R.raw.hello_linjun)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnCompletionListener {
                    release()
                    onDone()
                }
                setOnErrorListener { _, _, _ ->
                    release()
                    onDone()
                    true
                }
                start()
            } ?: onDone()
        }.onFailure {
            onDone()
        }
    }
}
