package com.zytrm.mommymods.media

import java.nio.file.InvalidPathException
import java.nio.file.Path

object MediaResolver {
    enum class Type {
        YOUTUBE,
        SPOTIFY,
        SOUNDCLOUD,
        BANDCAMP,
        TWITCH,
        DIRECT_HTTP,
        LOCAL_FILE,
        SEARCH,
    }

    fun detect(input: String): Type {
        val value = input.trim()
        val lower = value.lowercase()
        return when {
            lower.contains("spotify.com") || lower.startsWith("spotify:") -> Type.SPOTIFY
            lower.contains("youtube.com") || lower.contains("youtu.be") -> Type.YOUTUBE
            lower.contains("soundcloud.com") -> Type.SOUNDCLOUD
            lower.contains("bandcamp.com") -> Type.BANDCAMP
            lower.contains("twitch.tv") -> Type.TWITCH
            lower.startsWith("http://") || lower.startsWith("https://") -> Type.DIRECT_HTTP
            lower.startsWith("file://") || isLocalPath(value) -> Type.LOCAL_FILE
            else -> Type.SEARCH
        }
    }

    fun toIdentifier(input: String): String {
        val value = input.trim()
        return if (detect(value) == Type.SEARCH) "ytsearch:$value" else value
    }

    private fun isLocalPath(value: String): Boolean = try {
        Path.of(value).isAbsolute
    } catch (_: InvalidPathException) {
        false
    }
}
