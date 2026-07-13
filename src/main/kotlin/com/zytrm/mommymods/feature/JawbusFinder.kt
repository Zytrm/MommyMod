package com.zytrm.mommymods.feature

import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.core.GameContext
import com.zytrm.mommymods.ui.HudElement
import com.zytrm.mommymods.ui.HudLayout
import com.zytrm.mommymods.ui.UiStyle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

object JawbusFinder {
    private val deathMessage = Regex(
        "^\\s*☠\\s+(?:\\[[^]]+]\\s+)?([A-Za-z0-9_]{3,16})\\s+was killed by Lord Jawbus\\.\\s*$",
    )
    private const val DISPLAY_MILLIS = 10_000L
    private const val COOLDOWN_MILLIS = 45_000L

    @Volatile private var alertUntil = 0L
    @Volatile private var nextAllowedAt = 0L
    @Volatile private var playerName = ""

    fun onMessage(message: String) {
        val settings = ModConfig.values
        if (!settings.jawbusFinder || !settings.deathMessageDetection || !GameContext.isOnHypixel()) return
        val name = classify(message, Minecraft.getInstance().user.name, PartyState::isMember) ?: return

        val now = System.currentTimeMillis()
        if (now < nextAllowedAt) return
        playerName = name
        alertUntil = now + DISPLAY_MILLIS
        nextAllowedAt = now + COOLDOWN_MILLIS
    }

    internal fun extractVictim(message: String): String? = deathMessage.matchEntire(message)?.groupValues?.get(1)

    internal fun classify(message: String, localPlayer: String, partyMember: (String) -> Boolean): String? {
        val victim = extractVictim(message) ?: return null
        return victim.takeUnless { it.equals(localPlayer, ignoreCase = true) || partyMember(it) }
    }

    fun reset() {
        alertUntil = 0L
        nextAllowedAt = 0L
        playerName = ""
    }

    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor) {
        val now = System.currentTimeMillis()
        val remaining = alertUntil - now
        if (remaining <= 0L) return

        val minecraft = Minecraft.getInstance()
        val width = 236
        val height = 42
        val (x, y) = HudLayout.position(HudElement.JAWBUS, width, height, graphics.guiWidth(), graphics.guiHeight())
        val alpha = if (remaining < 600L) (remaining / 600.0 * 235).roundToInt() else 235
        val background = (alpha shl 24) or 0x231329
        val border = UiStyle.withAlpha(UiStyle.accent, alpha)
        val text = (alpha shl 24) or 0xFFD8E8
        val secondary = (alpha shl 24) or 0xC7A7D4

        graphics.fill(x, y, x + width, y + height, background)
        graphics.fill(x, y, x + width, y + 2, border)
        graphics.fill(x, y + height - 2, x + width, y + height, border)
        graphics.centeredText(minecraft.font, "JAWBUS IN THIS LOBBY", x + width / 2, y + 8, text)
        graphics.centeredText(minecraft.font, "$playerName was killed by Lord Jawbus", x + width / 2, y + 24, secondary)
    }
}
