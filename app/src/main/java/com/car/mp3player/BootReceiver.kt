package com.car.mp3player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.car.mp3player.data.SettingsRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != ACTION_QUICKBOOT &&
            action != ACTION_HTC_QUICKBOOT
        ) {
            return
        }
        val settings = SettingsRepository(context)
        if (!settings.bootAutoStart) return
        ContextCompat.startForegroundService(context, Intent(context, BootResumeService::class.java))
    }

    companion object {
        private const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
