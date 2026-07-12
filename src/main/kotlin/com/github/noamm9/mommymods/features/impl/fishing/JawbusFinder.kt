package com.github.noamm9.mommymods.features.impl.fishing

import com.github.noamm9.event.impl.ChatMessageEvent
import com.github.noamm9.event.impl.RenderOverlayEvent
import com.github.noamm9.features.Feature
import com.github.noamm9.mommymods.core.GameContext
import com.github.noamm9.ui.clickgui.components.impl.ToggleSetting
import com.github.noamm9.utils.PartyUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

object JawbusFinder : Feature(
    description = "Alerts when a non-party player dies to Jawbus in your lobby.",
    toggled = true,
) {
    private val deathMessage = Regex("^(?:\\[[^]]+] )?(\\w{1,16}) was killed by (?:Lord )?Jawbus\\.?$")
    private const val DISPLAY_MILLIS = 10_000L
    private const val COOLDOWN_MILLIS = 45_000L

    @Volatile private var alertUntil = 0L
    @Volatile private var nextAllowedAt = 0L
    @Volatile private var playerName = ""

    private val deathMessageDetection by ToggleSetting("Death Message Detection", true)
        .section("Detection")

    override fun init() {
        register<ChatMessageEvent> { onMessage(event.unformattedText) }
        register<RenderOverlayEvent> { render(event.context) }
    }

    override fun onDisable() {
        super.onDisable()
        alertUntil = 0L
    }

    private fun onMessage(message: String) {
        if (!deathMessageDetection.value || !GameContext.isOnHypixel()) return
        val name = deathMessage.matchEntire(message)?.groupValues?.get(1) ?: return
        if (name.equals(Minecraft.getInstance().user.name, ignoreCase = true) ||
            PartyUtils.members.any { it.equals(name, ignoreCase = true) }
        ) return

        val now = System.currentTimeMillis()
        if (now < nextAllowedAt) return
        playerName = name
        alertUntil = now + DISPLAY_MILLIS
        nextAllowedAt = now + COOLDOWN_MILLIS
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        val now = System.currentTimeMillis()
        val remaining = alertUntil - now
        if (remaining <= 0L) return

        val minecraft = Minecraft.getInstance()
        val width = 236
        val height = 42
        val x = (graphics.guiWidth() - width) / 2
        val y = 18
        val alpha = if (remaining < 600L) (remaining / 600.0 * 235).roundToInt() else 235
        val background = (alpha shl 24) or 0x231329
        val border = (alpha shl 24) or 0xF0659B
        val text = (alpha shl 24) or 0xFFD8E8
        val secondary = (alpha shl 24) or 0xC7A7D4

        graphics.fill(x, y, x + width, y + height, background)
        graphics.fill(x, y, x + width, y + 2, border)
        graphics.fill(x, y + height - 2, x + width, y + height, border)
        graphics.centeredText(minecraft.font, "JAWBUS IN THIS LOBBY", x + width / 2, y + 8, text)
        graphics.centeredText(minecraft.font, "$playerName was killed by Lord Jawbus", x + width / 2, y + 24, secondary)
    }
}
