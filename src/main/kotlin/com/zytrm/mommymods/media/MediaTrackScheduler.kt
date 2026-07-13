package com.zytrm.mommymods.media

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.zytrm.mommymods.MommyMods
import java.util.ArrayDeque

class MediaTrackScheduler(
    private val player: AudioPlayer,
    private val onStart: (MediaInfo) -> Unit,
    private val onError: (String) -> Unit,
) : AudioEventAdapter() {
    enum class RepeatMode { NONE, ONE, ALL }

    private val queue = ArrayDeque<AudioTrack>()
    private val history = ArrayDeque<AudioTrack>()

    @Volatile
    private var current: AudioTrack? = null

    @Volatile
    private var shuffled = false

    @Volatile
    private var repeatMode = RepeatMode.NONE

    @Synchronized
    fun playNow(track: AudioTrack, rememberCurrent: Boolean = true) {
        if (rememberCurrent) remember(current)
        current = track
        player.playTrack(track)
        onStart(track.toMediaInfo())
    }

    @Synchronized
    fun queue(track: AudioTrack) {
        queue.addLast(track)
    }

    @Synchronized
    fun next() {
        advance(automatic = false)
    }

    @Synchronized
    private fun advance(automatic: Boolean) {
        val previous = current
        if (automatic && repeatMode == RepeatMode.ONE && previous != null) {
            playNow(previous.makeClone(), rememberCurrent = false)
            return
        }
        if (repeatMode == RepeatMode.ALL && previous != null) {
            queue.addLast(previous.makeClone())
        }
        val next = queue.pollFirst()
        if (next == null) {
            stop(clearQueue = false)
            return
        }
        playNow(next)
    }

    @Synchronized
    fun previous() {
        val previous = history.pollFirst() ?: return
        current?.let { queue.addFirst(it.makeClone()) }
        playNow(previous, rememberCurrent = false)
    }

    @Synchronized
    fun stop(clearQueue: Boolean = true) {
        player.stopTrack()
        current = null
        if (clearQueue) queue.clear()
    }

    fun currentTrack(): AudioTrack? = current

    @Synchronized
    fun queueSize(): Int = queue.size

    @Synchronized
    fun toggleShuffle(): Boolean {
        shuffled = !shuffled
        if (shuffled && queue.size > 1) {
            val tracks = queue.toMutableList().apply { shuffle() }
            queue.clear()
            queue.addAll(tracks)
        }
        return shuffled
    }

    @Synchronized
    fun cycleRepeat(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.NONE
        }
        return repeatMode
    }

    fun isShuffled(): Boolean = shuffled

    fun repeatMode(): RepeatMode = repeatMode

    private fun remember(track: AudioTrack?) {
        if (track == null) return
        runCatching { track.makeClone() }.getOrNull()?.let {
            history.addFirst(it)
            while (history.size > 25) history.removeLast()
        }
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        if (endReason.mayStartNext) advance(automatic = true)
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        MommyMods.logger.warn("Media playback failed for {}", track.info.title, exception)
        onError("Playback failed: ${exception.message ?: "unknown error"}")
        next()
    }

    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        MommyMods.logger.warn("Media track stalled: {}", track.info.title)
        onError("Playback stalled.")
        next()
    }

    private fun AudioTrack.toMediaInfo(): MediaInfo {
        val uri = info.uri.orEmpty()
        val source = when {
            uri.contains("youtube.com") || uri.contains("youtu.be") -> MediaSource.YOUTUBE
            uri.contains("soundcloud.com") -> MediaSource.SOUNDCLOUD
            uri.contains("bandcamp.com") -> MediaSource.BANDCAMP
            uri.contains("twitch.tv") -> MediaSource.TWITCH
            uri.startsWith("http://") || uri.startsWith("https://") -> MediaSource.HTTP
            uri.isNotBlank() -> MediaSource.LOCAL
            else -> MediaSource.UNKNOWN
        }
        return MediaInfo(
            title = info.title ?: "Unknown title",
            author = info.author ?: "Unknown artist",
            uri = uri,
            durationMs = info.length,
            source = source,
        )
    }
}
