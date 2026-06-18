package com.car.mp3player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var fontSizeSp: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, 28f)
        set(value) = prefs.edit { putFloat(KEY_FONT_SIZE, value) }

    var highlightColor: Int
        get() = prefs.getInt(KEY_HIGHLIGHT, 0xFFFFD54F.toInt())
        set(value) = prefs.edit { putInt(KEY_HIGHLIGHT, value) }

    var pendingColor: Int
        get() = prefs.getInt(KEY_PENDING, 0x99FFFFFF.toInt())
        set(value) = prefs.edit { putInt(KEY_PENDING, value) }

    var nextLineColor: Int
        get() = prefs.getInt(KEY_NEXT, 0x66FFFFFF.toInt())
        set(value) = prefs.edit { putInt(KEY_NEXT, value) }

    var overlayPosition: Int
        get() = prefs.getInt(KEY_POSITION, POSITION_CENTER)
        set(value) = prefs.edit { putInt(KEY_POSITION, value) }

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, true)
        set(value) = prefs.edit { putBoolean(KEY_OVERLAY, value) }

    companion object {
        const val PREFS_NAME = "mp3_player_settings"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_HIGHLIGHT = "highlight_color"
        const val KEY_PENDING = "pending_color"
        const val KEY_NEXT = "next_line_color"
        const val KEY_POSITION = "overlay_position"
        const val KEY_OVERLAY = "overlay_enabled"

        const val POSITION_TOP = 0
        const val POSITION_CENTER = 1
        const val POSITION_BOTTOM = 2
    }
}
