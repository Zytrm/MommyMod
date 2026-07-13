package com.zytrm.mommymods.media

enum class MediaSource {
    YOUTUBE,
    SOUNDCLOUD,
    BANDCAMP,
    TWITCH,
    HTTP,
    LOCAL,
    UNKNOWN,
}

data class MediaInfo(
    val title: String,
    val author: String,
    val uri: String,
    val durationMs: Long,
    val source: MediaSource,
) {
    companion object {
        fun formatTime(milliseconds: Long): String {
            if (milliseconds <= 0L) return "0:00"
            val totalSeconds = milliseconds / 1_000L
            val hours = totalSeconds / 3_600L
            val minutes = totalSeconds % 3_600L / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
            else "%d:%02d".format(minutes, seconds)
        }
    }
}
