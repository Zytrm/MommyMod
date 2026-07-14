package com.zytrm.mommymods.feature

import com.zytrm.mommymods.MommyMods
import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.core.Chat
import com.zytrm.mommymods.media.MediaInfo
import com.zytrm.mommymods.media.MediaPlayerEngine
import com.zytrm.mommymods.media.MediaResolver
import com.zytrm.mommymods.media.MediaTrackScheduler
import com.zytrm.mommymods.ui.MediaPlayerScreen
import com.zytrm.mommymods.ui.HudElement
import com.zytrm.mommymods.ui.HudLayout
import com.zytrm.mommymods.ui.UiStyle
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import java.awt.Desktop
import java.net.URI
import kotlin.math.roundToInt

object MediaPlayer {
    @Volatile
    private var engine: MediaPlayerEngine? = null
    private var hudControlsRegistered = false

    @Volatile
    var status: String = "Ready"
        private set

    fun initialize() {
        registerHudControls()
        if (engine != null) return
        engine = runCatching {
            MediaPlayerEngine(
                initialVolume = ModConfig.values.mediaVolume,
                autoplay = { ModConfig.values.mediaAutoplay },
                onTrackStart = { info ->
                    status = "Playing"
                },
                onError = { message ->
                    status = message
                    Chat.info(message)
                },
            )
        }.onFailure { exception ->
            status = "Media player failed to initialize"
            MommyMods.logger.error("Could not initialize the media player", exception)
        }.getOrNull()
    }

    fun play(input: String) {
        if (!ModConfig.values.mediaPlayer) {
            Chat.info("Enable Aura Player in the Misc menu first.")
            return
        }
        val query = input.trim()
        if (query.isEmpty()) {
            openScreen(Minecraft.getInstance().screen)
            return
        }
        if (MediaResolver.detect(query) == MediaResolver.Type.SPOTIFY) {
            openSpotify(query)
            return
        }

        initialize()
        val activeEngine = engine ?: run {
            Chat.info(status)
            return
        }
        status = "Loading..."
        activeEngine.loadAndPlay(MediaResolver.toIdentifier(query))
    }

    fun togglePause() {
        engine?.togglePause()
        status = if (engine?.isPaused() == true) "Paused" else "Playing"
    }

    fun stop() {
        engine?.stop()
        status = "Stopped"
    }

    fun next() {
        engine?.next()
        status = "Skipping..."
    }

    fun previous() {
        engine?.previous()
        status = "Previous track"
    }

    fun toggleShuffle() {
        val enabled = engine?.toggleShuffle() ?: false
        status = "Shuffle ${if (enabled) "on" else "off"}"
    }

    fun cycleRepeat() {
        val mode = engine?.cycleRepeat() ?: MediaTrackScheduler.RepeatMode.NONE
        status = "Repeat ${mode.name.lowercase()}"
    }

    fun seekTo(positionMs: Long) {
        engine?.seekTo(positionMs)
    }

    fun isShuffled(): Boolean = engine?.isShuffled() == true

    fun repeatMode(): MediaTrackScheduler.RepeatMode = engine?.repeatMode() ?: MediaTrackScheduler.RepeatMode.NONE

    fun setVolume(value: Float, save: Boolean = true) {
        val volume = value.coerceIn(0f, 1f)
        ModConfig.values.mediaVolume = volume
        engine?.setVolume(volume)
        if (save) ModConfig.save()
    }

    fun onEnabledChanged(enabled: Boolean) {
        if (enabled) initialize() else stop()
    }

    fun openScreen(parent: Screen?) {
        if (!ModConfig.values.mediaPlayer) {
            Chat.info("Enable Aura Player in the Misc menu first.")
            return
        }
        initialize()
        Minecraft.getInstance().setScreen(MediaPlayerScreen(parent))
    }

    fun isPaused(): Boolean = engine?.isPaused() == true

    fun currentInfo(): MediaInfo? = engine?.currentInfo()

    fun positionMs(): Long = engine?.positionMs() ?: 0L

    fun durationMs(): Long = engine?.durationMs() ?: currentInfo()?.durationMs ?: 0L

    fun queueSize(): Int = engine?.queueSize() ?: 0

    fun shutdown() {
        engine?.shutdown()
        engine = null
    }

    @JvmStatic
    fun renderHud(graphics: GuiGraphicsExtractor) {
        if (!ModConfig.values.mediaPlayer || !ModConfig.values.mediaHud) return
        val info = currentInfo() ?: return
        val minecraft = Minecraft.getInstance()
        val width = 196
        val height = 38
        val (x, y) = HudLayout.position(HudElement.MEDIA, width, height, graphics.guiWidth(), graphics.guiHeight())
        val title = fit(info.title, 146, minecraft)
        val state = if (isPaused()) "Paused" else "${MediaInfo.formatTime(positionMs())} / ${MediaInfo.formatTime(durationMs())}"
        val progress = if (durationMs() > 0L) (positionMs().toDouble() / durationMs()).coerceIn(0.0, 1.0) else 0.0

        graphics.fill(x, y, x + width, y + height, 0xD9100C12.toInt())
        graphics.fill(x, y, x + 2, y + height, UiStyle.accent)
        graphics.text(minecraft.font, title, x + 9, y + 7, 0xFFFFE8F0.toInt(), false)
        graphics.text(minecraft.font, "$state  ${(ModConfig.values.mediaVolume * 100).roundToInt()}%", x + 9, y + 21, 0xFFCDB2BE.toInt(), false)
        graphics.fill(x + width - 25, y + 6, x + width - 8, y + 23, UiStyle.accentMuted)
        graphics.centeredText(minecraft.font, if (isPaused()) ">" else "||", x + width - 16, y + 11, 0xFFFFE8F0.toInt())
        graphics.fill(x + 9, y + 33, x + width - 9, y + 35, 0xFF44353C.toInt())
        graphics.fill(x + 9, y + 33, x + 9 + ((width - 18) * progress).toInt(), y + 35, UiStyle.accent)
    }

    private fun registerHudControls() {
        if (hudControlsRegistered) return
        hudControlsRegistered = true
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            ScreenMouseEvents.allowMouseClick(screen).register { _, event -> !handleHudClick(event.x, event.y, event.button()) }
            ScreenMouseEvents.allowMouseScroll(screen).register { _, mouseX, mouseY, _, vertical ->
                !handleHudScroll(mouseX, mouseY, vertical)
            }
        }
    }

    private fun handleHudClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0 || !ModConfig.values.mediaPlayer || !ModConfig.values.mediaHud || currentInfo() == null) return false
        val minecraft = Minecraft.getInstance()
        val width = 196
        val height = 38
        val (x, y) = HudLayout.position(HudElement.MEDIA, width, height, minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight)
        if (mouseX.toInt() !in (x + width - 25) until (x + width - 8) || mouseY.toInt() !in (y + 6) until (y + 23)) {
            return false
        }
        togglePause()
        UiStyle.playClick()
        return true
    }

    private fun handleHudScroll(mouseX: Double, mouseY: Double, vertical: Double): Boolean {
        if (vertical == 0.0 || !ModConfig.values.mediaPlayer || !ModConfig.values.mediaHud || currentInfo() == null) return false
        val minecraft = Minecraft.getInstance()
        val width = 196
        val height = 38
        val (x, y) = HudLayout.position(HudElement.MEDIA, width, height, minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight)
        if (mouseX.toInt() !in x until (x + width) || mouseY.toInt() !in y until (y + height)) return false
        val direction = if (vertical > 0.0) 1 else -1
        val volume = ((ModConfig.values.mediaVolume + direction * 0.05f) * 20f).roundToInt() / 20f
        setVolume(volume.coerceIn(0f, 1f))
        UiStyle.playClick(1.15f)
        return true
    }

    private fun fit(value: String, maxWidth: Int, minecraft: Minecraft): String {
        if (minecraft.font.width(value) <= maxWidth) return value
        var shortened = value
        while (shortened.isNotEmpty() && minecraft.font.width("$shortened...") > maxWidth) {
            shortened = shortened.dropLast(1)
        }
        return "$shortened..."
    }

    private fun openSpotify(url: String) {
        val result = runCatching {
            check(Desktop.isDesktopSupported()) { "Desktop links are unavailable." }
            val appUri = spotifyAppUri(url)
            val openedApp = appUri != null && runCatching { Desktop.getDesktop().browse(URI(appUri)) }.isSuccess
            if (!openedApp) Desktop.getDesktop().browse(URI(url))
            if (openedApp) "Opened in Spotify." else "Opened Spotify in your browser."
        }.getOrElse { "Could not open Spotify: ${it.message ?: "unknown error"}" }
        status = result
        Chat.info(result)
    }

    private fun spotifyAppUri(url: String): String? {
        val match = Regex("open\\.spotify\\.com/(track|album|playlist|artist)/([A-Za-z0-9]+)").find(url) ?: return null
        return "spotify:${match.groupValues[1]}:${match.groupValues[2]}"
    }
}
