package com.car.mp3player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.car.mp3player.model.AppThemePreset
import com.car.mp3player.model.LyricFontFamily
import com.car.mp3player.model.LyricThemePreset
import com.car.mp3player.model.ThemeMode

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var fontSizeSp: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, 24f)
        set(value) = prefs.edit { putFloat(KEY_FONT_SIZE, value) }

    var playerFontSizeSp: Float
        get() = prefs.getFloat(KEY_PLAYER_FONT_SIZE, 20f)
        set(value) = prefs.edit { putFloat(KEY_PLAYER_FONT_SIZE, value) }

    var playerNextFontSizeSp: Float
        get() = prefs.getFloat(KEY_PLAYER_NEXT_FONT_SIZE, 17f)
        set(value) = prefs.edit { putFloat(KEY_PLAYER_NEXT_FONT_SIZE, value) }

    var currentLineScale: Float
        get() = prefs.getFloat(KEY_CURRENT_SCALE, 1.05f)
        set(value) = prefs.edit { putFloat(KEY_CURRENT_SCALE, value) }

    var nextLineScale: Float
        get() = prefs.getFloat(KEY_NEXT_SCALE, 0.92f)
        set(value) = prefs.edit { putFloat(KEY_NEXT_SCALE, value) }

    var maxLyricVisualLines: Int
        get() = prefs.getInt(KEY_MAX_VISUAL_LINES, 2).coerceIn(1, 4)
        set(value) = prefs.edit { putInt(KEY_MAX_VISUAL_LINES, value.coerceIn(1, 4)) }

    var smoothLyrics: Boolean
        get() = prefs.getBoolean(KEY_SMOOTH_LYRICS, true)
        set(value) = prefs.edit { putBoolean(KEY_SMOOTH_LYRICS, value) }

    var highlightColor: Int
        get() = prefs.getInt(KEY_HIGHLIGHT, LyricThemePreset.NETEASE.highlightColor)
        set(value) = prefs.edit { putInt(KEY_HIGHLIGHT, value) }

    var pendingColor: Int
        get() = prefs.getInt(KEY_PENDING, LyricThemePreset.NETEASE.pendingColor)
        set(value) = prefs.edit { putInt(KEY_PENDING, value) }

    var nextLineColor: Int
        get() = prefs.getInt(KEY_NEXT, LyricThemePreset.NETEASE.nextLineColor)
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

    var bootAutoStart: Boolean
        get() = prefs.getBoolean(KEY_BOOT_AUTO_START, false)
        set(value) = prefs.edit { putBoolean(KEY_BOOT_AUTO_START, value) }

    var clusterLyricsEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLUSTER_LYRICS, false)
        set(value) = prefs.edit { putBoolean(KEY_CLUSTER_LYRICS, value) }

    fun lyricSearchTitle(path: String): String? =
        prefs.getString(lyricTitleKey(path), null)

    fun lyricSearchArtist(path: String): String? =
        prefs.getString(lyricArtistKey(path), null)

    fun setLyricSearchOverride(path: String, title: String, artist: String) {
        prefs.edit {
            putString(lyricTitleKey(path), title.trim())
            putString(lyricArtistKey(path), artist.trim())
        }
    }

    fun clearLyricSearchOverride(path: String) {
        prefs.edit {
            remove(lyricTitleKey(path))
            remove(lyricArtistKey(path))
        }
    }

    private fun lyricTitleKey(path: String) = "lyric_title_${path.hashCode()}"
    private fun lyricArtistKey(path: String) = "lyric_artist_${path.hashCode()}"

    var themeMode: ThemeMode
        get() = ThemeMode.entries[prefs.getInt(KEY_THEME, ThemeMode.SYSTEM.ordinal)]
        set(value) = prefs.edit { putInt(KEY_THEME, value.ordinal) }

    fun appTheme(): AppThemePreset = AppThemePreset.fromId(
        prefs.getString(KEY_APP_THEME, AppThemePreset.NETEASE.id) ?: AppThemePreset.NETEASE.id
    )

    fun setAppTheme(preset: AppThemePreset) {
        prefs.edit { putString(KEY_APP_THEME, preset.id) }
    }

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

    fun lyricTheme(): LyricThemePreset = LyricThemePreset.fromId(
        prefs.getString(KEY_LYRIC_THEME, LyricThemePreset.NETEASE.id) ?: LyricThemePreset.NETEASE.id
    )

    fun lyricFontFamily(): LyricFontFamily = LyricFontFamily.fromId(
        prefs.getString(KEY_LYRIC_FONT, LyricFontFamily.DEFAULT.id) ?: LyricFontFamily.DEFAULT.id
    )

    fun setLyricTheme(preset: LyricThemePreset) {
        prefs.edit {
            putString(KEY_LYRIC_THEME, preset.id)
            putInt(KEY_HIGHLIGHT, preset.highlightColor)
            putInt(KEY_PENDING, preset.pendingColor)
            putInt(KEY_NEXT, preset.nextLineColor)
            putFloat(KEY_PLAYER_FONT_SIZE, preset.playerCurrentSizeSp)
            putFloat(KEY_PLAYER_NEXT_FONT_SIZE, preset.playerNextSizeSp)
            putFloat(KEY_FONT_SIZE, preset.overlaySizeSp)
        }
    }

    fun setLyricFontFamily(family: LyricFontFamily) {
        prefs.edit { putString(KEY_LYRIC_FONT, family.id) }
    }

    fun scanPaths(): List<String> =
        scanPathsText.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun scanTreeUris(): List<String> =
        scanTreeUrisText.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun allScanEntries(): List<String> = scanPaths() + scanTreeUris()

    fun addScanPath(path: String) {
        val paths = scanPaths().toMutableSet()
        if (paths.add(path)) scanPathsText = paths.joinToString("\n")
    }

    fun addScanTreeUri(uri: String) {
        val uris = scanTreeUris().toMutableSet()
        if (uris.add(uri)) scanTreeUrisText = uris.joinToString("\n")
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
        const val KEY_PLAYER_FONT_SIZE = "player_font_size"
        const val KEY_PLAYER_NEXT_FONT_SIZE = "player_next_font_size"
        const val KEY_CURRENT_SCALE = "current_line_scale"
        const val KEY_NEXT_SCALE = "next_line_scale"
        const val KEY_MAX_VISUAL_LINES = "max_visual_lines"
        const val KEY_SMOOTH_LYRICS = "smooth_lyrics"
        const val KEY_HIGHLIGHT = "highlight_color"
        const val KEY_PENDING = "pending_color"
        const val KEY_NEXT = "next_line_color"
        const val KEY_POSITION = "overlay_position"
        const val KEY_OVERLAY = "overlay_enabled"
        const val KEY_AUTO_RESUME = "auto_resume"
        const val KEY_BOOT_AUTO_START = "boot_auto_start"
        const val KEY_CLUSTER_LYRICS = "cluster_lyrics"
        const val KEY_THEME = "theme_mode"
        const val KEY_APP_THEME = "app_theme"
        const val KEY_LYRIC_THEME = "lyric_theme"
        const val KEY_LYRIC_FONT = "lyric_font"
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
