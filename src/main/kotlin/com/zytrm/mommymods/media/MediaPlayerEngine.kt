package com.zytrm.mommymods.media

import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.zytrm.mommymods.MommyMods
import dev.lavalink.youtube.YoutubeAudioSourceManager
import dev.lavalink.youtube.clients.AndroidVrWithThumbnail
import dev.lavalink.youtube.clients.MusicWithThumbnail
import dev.lavalink.youtube.clients.TvHtml5SimplyWithThumbnail
import dev.lavalink.youtube.clients.WebEmbeddedWithThumbnail
import dev.lavalink.youtube.clients.WebWithThumbnail
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

class MediaPlayerEngine(
    initialVolume: Float,
    private val autoplay: () -> Boolean,
    private val onTrackStart: (MediaInfo) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val manager = DefaultAudioPlayerManager()
    private val youtube = YoutubeAudioSourceManager(
        true,
        MusicWithThumbnail(),
        TvHtml5SimplyWithThumbnail(),
        AndroidVrWithThumbnail(),
        WebEmbeddedWithThumbnail(),
        WebWithThumbnail(),
    )
    private val player = manager.createPlayer()
    private val scheduler = MediaTrackScheduler(player, onTrackStart, onError)
    private val outputExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MommyMods-MediaOutput").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(true)
    private val outputStarted = AtomicBoolean(false)

    @Volatile
    private var paused = false

    @Volatile
    private var volume = initialVolume.coerceIn(0f, 1f)

    init {
        youtube.setCipherManager(MediaYoutubeCipherManager())
        manager.registerSourceManager(youtube)
        AudioSourceManagers.registerRemoteSources(
            manager,
            com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager::class.java,
        )
        AudioSourceManagers.registerLocalSource(manager)
        manager.configuration.outputFormat = Pcm16AudioDataFormat(2, SAMPLE_RATE.toInt(), FRAME_SAMPLES, false)
        player.addListener(scheduler)
        player.volume = (volume * 100f).toInt()
    }

    fun loadAndPlay(identifier: String) {
        ensureOutput()
        manager.loadItemOrdered(this, identifier, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) {
                scheduler.playNow(track)
            }

            override fun playlistLoaded(playlist: AudioPlaylist) {
                val tracks = playlist.tracks
                if (tracks.isEmpty()) {
                    onError("Playlist is empty.")
                    return
                }

                if (playlist.isSearchResult || !autoplay()) {
                    scheduler.playNow(playlist.selectedTrack ?: tracks.first())
                    return
                }

                val first = playlist.selectedTrack ?: tracks.first()
                scheduler.playNow(first)
                tracks.asSequence().filter { it !== first }.forEach(scheduler::queue)
            }

            override fun noMatches() {
                if (identifier.startsWith("ytsearch:")) onError("No media found.")
                else loadAndPlay("ytsearch:$identifier")
            }

            override fun loadFailed(exception: FriendlyException) {
                MommyMods.logger.warn("Could not load media identifier {}", identifier, exception)
                onError("Could not load media: ${exception.message ?: "unknown error"}")
            }
        })
    }

    fun togglePause() {
        if (paused) resume() else pause()
    }

    fun pause() {
        paused = true
        player.isPaused = true
    }

    fun resume() {
        paused = false
        player.isPaused = false
        ensureOutput()
    }

    fun stop() {
        paused = false
        scheduler.stop()
    }

    fun next() = scheduler.next()

    fun previous() = scheduler.previous()

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        player.volume = (volume * 100f).toInt()
    }

    fun getVolume(): Float = volume

    fun isPaused(): Boolean = paused

    fun isPlaying(): Boolean = scheduler.currentTrack() != null && !paused

    fun currentInfo(): MediaInfo? {
        val track = scheduler.currentTrack() ?: return null
        val uri = track.info.uri.orEmpty()
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
            track.info.title ?: "Unknown title",
            track.info.author ?: "Unknown artist",
            uri,
            track.info.length,
            source,
        )
    }

    fun positionMs(): Long = scheduler.currentTrack()?.position ?: 0L

    fun durationMs(): Long = scheduler.currentTrack()?.info?.length ?: 0L

    fun queueSize(): Int = scheduler.queueSize()

    fun toggleShuffle(): Boolean = scheduler.toggleShuffle()

    fun cycleRepeat(): MediaTrackScheduler.RepeatMode = scheduler.cycleRepeat()

    fun isShuffled(): Boolean = scheduler.isShuffled()

    fun repeatMode(): MediaTrackScheduler.RepeatMode = scheduler.repeatMode()

    fun seekTo(positionMs: Long) {
        val track = scheduler.currentTrack() ?: return
        if (track.isSeekable) track.position = positionMs.coerceIn(0L, track.info.length.coerceAtLeast(0L))
    }

    fun shutdown() {
        if (!running.getAndSet(false)) return
        scheduler.stop()
        outputExecutor.shutdownNow()
        player.destroy()
        manager.shutdown()
    }

    private fun ensureOutput() {
        if (!outputStarted.compareAndSet(false, true)) return
        outputExecutor.execute(::outputLoop)
    }

    private fun outputLoop() {
        var line: SourceDataLine? = null
        try {
            val format = AudioFormat(SAMPLE_RATE, 16, 2, true, false)
            line = AudioSystem.getSourceDataLine(format)
            line.open(format, FRAME_BYTES * 8)
            line.start()

            while (running.get() && !Thread.currentThread().isInterrupted) {
                if (paused) {
                    Thread.sleep(20L)
                    continue
                }
                val frame = player.provide(100L, TimeUnit.MILLISECONDS) ?: continue
                if (!frame.isTerminator) line.write(frame.data, 0, frame.data.size)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (exception: Exception) {
            MommyMods.logger.error("Could not open the media audio output", exception)
            onError("Could not open the audio output device.")
            outputStarted.set(false)
        } finally {
            line?.runCatching {
                stop()
                flush()
                close()
            }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 48_000f
        private const val FRAME_SAMPLES = 960
        private const val FRAME_BYTES = FRAME_SAMPLES * 2 * 2
    }
}
