package com.car.mp3player.data

import java.io.File

object PodcastPaths {
    const val DEFAULT_ARTIST = "勾手老大爷邓肯"

    fun normalize(path: String): String =
        path.trim().trimEnd('/', '\\')

    fun isUnderPodcast(path: String, podcastRoots: List<String>): Boolean {
        if (podcastRoots.isEmpty()) return false
        val target = normalize(path)
        return podcastRoots.any { root ->
            val base = normalize(root)
            target == base || target.startsWith("$base/") || target.startsWith("$base\\")
        }
    }

    fun defaultFolders(): List<String> = listOf(
        "/storage/emulated/0/Music/邓肯",
        "/sdcard/Music/邓肯"
    )

    fun ensureDefaultFolder(): File {
        val folder = File(defaultFolders().first())
        if (!folder.exists()) {
            runCatching { folder.mkdirs() }
        }
        return folder
    }
}
