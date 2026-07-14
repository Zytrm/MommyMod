package com.zytrm.mommymods.ui

import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.feature.MediaPlayer
import com.zytrm.mommymods.media.MediaInfo
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

class MediaPlayerScreen(private val parent: Screen?) : Screen(Component.literal("MommyMods Aura Player")) {
    private lateinit var input: EditBox
    private var panelX = 0
    private var panelY = 0

    override fun init() {
        super.init()
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT) / 2
        input = EditBox(font, panelX + 12, panelY + 35, PANEL_WIDTH - 78, 18, Component.literal("Media URL or search"))
        input.setMaxLength(512)
        input.setHint(Component.literal("YouTube, SoundCloud, URL, file, or search"))
        input.isFocused = true
        addRenderableWidget(input)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT) / 2

        graphics.fill(0, 0, width, height, 0x72000000)
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, 0xE0000000.toInt())
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF20B080D.toInt())
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, UiStyle.accent)
        graphics.fill(panelX + 2, panelY + 2, panelX + PANEL_WIDTH - 2, panelY + 25, 0xF01A151C.toInt())
        graphics.centeredText(font, "AURA PLAYER", panelX + PANEL_WIDTH / 2, panelY + 9, 0xFFFFE8F0.toInt())

        val goHovered = mouseX in (panelX + PANEL_WIDTH - 58) until (panelX + PANEL_WIDTH - 12) &&
            mouseY in (panelY + 35) until (panelY + 53)
        graphics.fill(
            panelX + PANEL_WIDTH - 58,
            panelY + 35,
            panelX + PANEL_WIDTH - 12,
            panelY + 53,
            if (goHovered) UiStyle.accentBright else UiStyle.accentMuted,
        )
        graphics.centeredText(font, "PLAY", panelX + PANEL_WIDTH - 35, panelY + 40, 0xFFFFE8F0.toInt())

        val info = MediaPlayer.currentInfo()
        val track = info?.let { fit(it.title, PANEL_WIDTH - 24) } ?: MediaPlayer.status
        graphics.centeredText(font, track, panelX + PANEL_WIDTH / 2, panelY + 61, 0xFFCDB2BE.toInt())

        val duration = MediaPlayer.durationMs()
        val position = MediaPlayer.positionMs()
        val progress = if (duration > 0L) (position.toDouble() / duration).coerceIn(0.0, 1.0) else 0.0
        graphics.fill(panelX + 12, panelY + 76, panelX + PANEL_WIDTH - 12, panelY + 79, 0xFF44353C.toInt())
        graphics.fill(panelX + 12, panelY + 76, panelX + 12 + ((PANEL_WIDTH - 24) * progress).toInt(), panelY + 79, UiStyle.accent)
        val time = "${MediaInfo.formatTime(position)} / ${MediaInfo.formatTime(duration)}"
        graphics.text(font, time, panelX + 12, panelY + 83, 0xFF9E8792.toInt(), false)
        val volume = "${(ModConfig.values.mediaVolume * 100).roundToInt()}%"
        graphics.centeredText(font, volume, panelX + PANEL_WIDTH / 2, panelY + 83, 0xFF9E8792.toInt())
        val queued = MediaPlayer.queueSize()
        if (queued > 0) {
            val queueText = "+$queued queued"
            graphics.text(font, queueText, panelX + PANEL_WIDTH - 12 - font.width(queueText), panelY + 83, 0xFF9E8792.toInt(), false)
        }

        controls().forEach { button ->
            val hovered = mouseX in button.x until (button.x + button.width) && mouseY in button.y until (button.y + 18)
            graphics.fill(button.x, button.y, button.x + button.width, button.y + 18, if (hovered) UiStyle.accentBright else UiStyle.accentMuted)
            graphics.centeredText(font, button.label, button.x + button.width / 2, button.y + 5, 0xFFFFE8F0.toInt())
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() == 0) {
            if (event.x.toInt() in (panelX + PANEL_WIDTH - 58) until (panelX + PANEL_WIDTH - 12) &&
                event.y.toInt() in (panelY + 35) until (panelY + 53)
            ) {
                UiStyle.playClick()
                playInput()
                return true
            }
            if (event.x.toInt() in (panelX + 12)..(panelX + PANEL_WIDTH - 12) &&
                event.y.toInt() in (panelY + 73)..(panelY + 82)
            ) {
                val duration = MediaPlayer.durationMs()
                if (duration > 0L) {
                    val progress = ((event.x - panelX - 12) / (PANEL_WIDTH - 24).toDouble()).coerceIn(0.0, 1.0)
                    MediaPlayer.seekTo((duration * progress).toLong())
                }
                return true
            }
            controls().firstOrNull { event.x.toInt() in it.x until (it.x + it.width) && event.y.toInt() in it.y until (it.y + 18) }
                ?.let {
                    UiStyle.playClick()
                    it.action()
                    return true
                }
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (vertical != 0.0 && mouseX.toInt() in panelX until (panelX + PANEL_WIDTH) &&
            mouseY.toInt() in panelY until (panelY + PANEL_HEIGHT)
        ) {
            val direction = if (vertical > 0.0) 1 else -1
            val current = ModConfig.values.mediaVolume
            MediaPlayer.setVolume((((current + direction * 0.05f) * 20f).roundToInt() / 20f).coerceIn(0f, 1f))
            UiStyle.playClick(1.15f)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key == GLFW.GLFW_KEY_ENTER || event.key == GLFW.GLFW_KEY_KP_ENTER) {
            playInput()
            return true
        }
        return super.keyPressed(event)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    override fun isInGameUi(): Boolean = true

    private fun controls(): List<Control> {
        val gap = 5
        val widths = listOf(38, 70, 38, 46)
        val total = widths.sum() + gap * (widths.size - 1)
        var x = panelX + (PANEL_WIDTH - total) / 2
        val y = panelY + 101
        val labels = listOf("<", if (MediaPlayer.isPaused()) "RESUME" else "PAUSE", ">", "STOP")
        val actions = listOf<() -> Unit>(MediaPlayer::previous, MediaPlayer::togglePause, MediaPlayer::next, MediaPlayer::stop)
        val playback = widths.indices.map { index ->
            Control(x, y, widths[index], labels[index], actions[index]).also { x += widths[index] + gap }
        }
        val secondaryWidth = 105
        val secondaryGap = 8
        val secondaryX = panelX + (PANEL_WIDTH - secondaryWidth * 2 - secondaryGap) / 2
        val repeat = MediaPlayer.repeatMode().name
        return playback + listOf(
            Control(
                secondaryX,
                panelY + 125,
                secondaryWidth,
                "SHUFFLE ${if (MediaPlayer.isShuffled()) "ON" else "OFF"}",
                MediaPlayer::toggleShuffle,
            ),
            Control(
                secondaryX + secondaryWidth + secondaryGap,
                panelY + 125,
                secondaryWidth,
                "REPEAT $repeat",
                MediaPlayer::cycleRepeat,
            ),
        )
    }

    private fun playInput() {
        val value = input.value.trim()
        if (value.isNotEmpty()) MediaPlayer.play(value)
    }

    private fun fit(value: String, maxWidth: Int): String {
        if (font.width(value) <= maxWidth) return value
        var result = value
        while (result.isNotEmpty() && font.width("$result...") > maxWidth) result = result.dropLast(1)
        return "$result..."
    }

    private data class Control(val x: Int, val y: Int, val width: Int, val label: String, val action: () -> Unit)

    companion object {
        private const val PANEL_WIDTH = 286
        private const val PANEL_HEIGHT = 147
    }
}
