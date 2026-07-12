package com.github.noamm9.mommymods.features.impl.fishing

import com.github.noamm9.event.impl.ChatMessageEvent
import com.github.noamm9.event.impl.TickEvent
import com.github.noamm9.features.Feature
import com.github.noamm9.mommymods.MommyMods
import com.github.noamm9.mommymods.core.Chat
import com.github.noamm9.mommymods.core.GameContext
import com.github.noamm9.ui.clickgui.components.impl.ButtonSetting
import com.github.noamm9.ui.clickgui.components.impl.TextInputSetting
import com.github.noamm9.utils.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component

object LootingVMessage : Feature(
    description = "Sends one configurable Looting V reminder when you spawn a Jawbus.",
    toggled = true,
) {
    private val localJawbusSpawn = Regex(
        "^You have angered a legendary creature\\.\\.\\. (?:Lord )?Jawbus has arrived\\.?$",
        RegexOption.IGNORE_CASE,
    )
    private const val SPAWN_DEBOUNCE_MILLIS = 30_000L
    private const val MAX_CHAT_LENGTH = 256

    private var trackedLevel: ClientLevel? = null
    private var lastSentAt = 0L

    private val message by TextInputSetting(
        "Message",
        "Please do not kill my Jawbus unless you have Looting V.",
    ).section("Chat Message")

    @Suppress("unused")
    private val preview by ButtonSetting("Preview Message") { debugPreview() }
        .section("Chat Message")

    override fun init() {
        register<ChatMessageEvent> { onMessage(event.unformattedText) }
        register<TickEvent.End> { onTick(mc) }
    }

    override fun onDisable() {
        super.onDisable()
        lastSentAt = 0L
    }

    private fun onMessage(message: String) {
        if (!GameContext.isOnHypixel()) return
        if (!localJawbusSpawn.matches(message)) return

        val now = System.currentTimeMillis()
        if (now - lastSentAt < SPAWN_DEBOUNCE_MILLIS) return
        val outgoing = configuredMessage() ?: return
        if (Minecraft.getInstance().connection == null) return
        lastSentAt = now
        runCatching { ChatUtils.sendMessage(outgoing) }
            .onFailure {
                lastSentAt = 0L
                MommyMods.logger.warn("Could not queue the configured Jawbus message", it)
            }
    }

    private fun onTick(minecraft: Minecraft) {
        val level = minecraft.level
        if (level !== trackedLevel) {
            trackedLevel = level
            lastSentAt = 0L
        }
    }

    fun debugPreview() {
        val outgoing = configuredMessage()
        Chat.info(
            "Looting V Message: enabled=$enabled, " +
                "spawnPattern=valid, debounce=${SPAWN_DEBOUNCE_MILLIS / 1000}s.",
        )
        if (outgoing == null) {
            Chat.info("Preview unavailable: configure a non-empty message that does not begin with '/'.")
            return
        }
        Chat.component(
            Component.literal("Preview only - no chat was sent: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(outgoing).withStyle(ChatFormatting.WHITE)),
        )
    }

    private fun configuredMessage(): String? {
        val message = message.value
            .replace(Regex("[\\r\\n]+"), " ")
            .trim()
            .take(MAX_CHAT_LENGTH)
        if (message.isBlank() || message.startsWith('/')) return null
        return message
    }
}
