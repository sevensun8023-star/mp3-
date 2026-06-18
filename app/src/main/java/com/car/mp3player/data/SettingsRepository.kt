package com.car.mp3player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.car.mp3player.model.ThemeMode

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var fontSizeSp: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, 28f)
        set(value) = prefs.edit { putFloat(KEY_FONT_SIZE, value) }

    var highlightColor: Int
        get() = prefs.getInt(KEY_HIGHLIGHT, 0xFFEC4141.toInt())
        set(value) = prefs.edit { putInt(KEY_HIGHLIGHT, value) }

    var pendingColor: Int
        get() = prefs.getInt(KEY_PENDING, 0x661F1F1F)
        set(value) = prefs.edit { putInt(KEY_PENDING, value) }

    var nextLineColor: Int
        get() = prefs.getInt(KEY_NEXT, 0x44888888)
        set(value) = prefs.edit { putInt(KEY_NEXT, value) }

    var overlayPosition: Int
        get() = prefs.getInt(KEY_POSITION, POSITION_CENTER)
        set(value) = prefs.edit { putInt(KEY_POSITION, value) }

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, true)
        set(value) = prefs.edit { putBoolean(KEY_OVERLAY, value) }

    var autoResumePlayback: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESUME, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_RESUME, value) }

    var themeMode: ThemeMode
        get() = ThemeMode.entries[prefs.getInt(KEY_THEME, ThemeMode.SYSTEM.ordinal)]
        set(value) = prefs.edit { putInt(KEY_THEME, value.ordinal) }

    var lastSongPath: String?
        get() = prefs.getString(KEY_LAST_SONG, null)
        set(value) = prefs.edit { putString(KEY_LAST_SONG, value) }

    var lastPositionMs: Long
        get() = prefs.getLong(KEY_LAST_POSITION, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_POSITION, value) }

    var scanPathsText: String
        get() = prefs.getString(KEY_SCAN_PATHS, DEFAULT_SCAN_PATHS) ?: DEFAULT_SCAN_PATHS
        set(value) = prefs.edit { putString(KEY_SCAN_PATHS, value) }

    var scanTreeUrisText: String
        get() = prefs.getString(KEY_SCAN_TREE_URIS, "") ?: ""
        set(value) = prefs.edit { putString(KEY_SCAN_TREE_URIS, value) }

    var onlineLyricsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ONLINE_LYRICS, true)
        set(value) = prefs.edit { putBoolean(KEY_ONLINE_LYRICS, value) }

    var onlineCoverEnabled: Boolean
        get() = prefs.getBoolean(KEY_ONLINE_COVER, true)
        set(value) = prefs.edit { putBoolean(KEY_ONLINE_COVER, value) }

    fun scanPaths(): List<String> {
        return scanPathsText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun scanTreeUris(): List<String> {
        return scanTreeUrisText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun allScanEntries(): List<String> = scanPaths() + scanTreeUris()

    fun addScanPath(path: String) {
        val paths = scanPaths().toMutableSet()
        if (paths.add(path)) {
            scanPathsText = paths.joinToString("\n")
        }
    }

    fun addScanTreeUri(uri: String) {
        val uris = scanTreeUris().toMutableSet()
        if (uris.add(uri)) {
            scanTreeUrisText = uris.joinToString("\n")
        }
    }

    fun removeScanEntry(entry: String) {
        if (entry.startsWith("content://")) {
            val uris = scanTreeUris().toMutableSet()
            uris.remove(entry)
            scanTreeUrisText = uris.joinToString("\n")
        } else {
            val paths = scanPaths().toMutableSet()
            paths.remove(entry)
            scanPathsText = paths.joinToString("\n")
        }
    }

    fun applyTheme() {
        val mode = when (themeMode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    companion object {
        const val PREFS_NAME = "mp3_player_settings"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_HIGHLIGHT = "highlight_color"
        const val KEY_PENDING = "pending_color"
        const val KEY_NEXT = "next_line_color"
        const val KEY_POSITION = "overlay_position"
        const val KEY_OVERLAY = "overlay_enabled"
        const val KEY_AUTO_RESUME = "auto_resume"
        const val KEY_THEME = "theme_mode"
        const val KEY_LAST_SONG = "last_song_path"
        const val KEY_LAST_POSITION = "last_position_ms"
        const val KEY_SCAN_PATHS = "scan_paths"
        const val KEY_SCAN_TREE_URIS = "scan_tree_uris"
        const val KEY_ONLINE_LYRICS = "online_lyrics"
        const val KEY_ONLINE_COVER = "online_cover"

        const val POSITION_TOP = 0
        const val POSITION_CENTER = 1
        const val POSITION_BOTTOM = 2

        const val DEFAULT_SCAN_PATHS = "/sdcard/Music\n/storage/emulated/0/Music"
    }
}
