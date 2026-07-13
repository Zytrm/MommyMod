package com.zytrm.mommymods.feature

import com.zytrm.mommymods.MommyMods
import com.zytrm.mommymods.config.DEFAULT_FINISHER_MESSAGE
import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.core.Chat
import com.zytrm.mommymods.core.GameContext
import com.zytrm.mommymods.ui.UiStyle
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import kotlin.math.max
import kotlin.math.roundToInt

object JawbusFinisherHelper {
    internal data class HealthSample(val current: Double, val maximum: Double?)

    private val localJawbusSpawn = Regex(
        "^You have angered a legendary creature\\.\\.\\. (?:Lord )?Jawbus has arrived\\.?$",
        RegexOption.IGNORE_CASE,
    )
    private val healthText = Regex(
        "([0-9]+(?:\\.[0-9]+)?)([kKmMbBtT]?)(?:\\s*/\\s*([0-9]+(?:\\.[0-9]+)?)([kKmMbBtT]?))?\\s*[\\u2764\\u2665]",
    )
    private const val TRACKING_TIMEOUT_MILLIS = 10 * 60 * 1000L
    private const val DISPLAY_MILLIS = 10_000L
    private const val MAX_MESSAGE_LENGTH = 252

    private var trackedLevel: ClientLevel? = null
    private var spawnAt = 0L
    private var maximumObserved = 0.0
    private var triggered = false
    private var alertUntil = 0L
    private var alertPlayers = ""
    private var alertHealth = 0

    fun onMessage(message: String) {
        if (!GameContext.isOnHypixel() || !localJawbusSpawn.matches(message)) return
        val now = System.currentTimeMillis()
        ModConfig.values.lastJawbusHookedAt = now
        ModConfig.save()
        FishingPartyHelper.refreshPartyReadiness(force = true)

        trackedLevel = Minecraft.getInstance().level
        spawnAt = now
        maximumObserved = 0.0
        triggered = false
        if (!ModConfig.values.jawbusFinisherEnabled) return
        MommyMods.logger.debug("Tracking locally hooked Jawbus for finisher health")
    }

    fun onTick(minecraft: Minecraft) {
        val level = minecraft.level
        if (level !== trackedLevel) {
            trackedLevel = level
            resetActive()
        }
        if (!ModConfig.values.jawbusFinisherEnabled || !GameContext.isOnHypixel()) return
        if (spawnAt == 0L || triggered || level == null) return

        val now = System.currentTimeMillis()
        if (now - spawnAt > TRACKING_TIMEOUT_MILLIS) {
            resetActive()
            return
        }
        val player = minecraft.player ?: return
        val sample = level.entitiesForRendering()
            .asSequence()
            .filter { it.distanceToSqr(player) <= 10_000.0 }
            .mapNotNull { entity -> jawbusHealth(entity)?.let { entity.distanceToSqr(player) to it } }
            .minByOrNull { it.first }
            ?.second ?: return

        maximumObserved = max(maximumObserved, sample.maximum ?: sample.current)
        if (maximumObserved <= 0.0) return
        val healthPercent = (sample.current / maximumObserved * 100.0).coerceIn(0.0, 100.0)
        if (healthPercent > ModConfig.values.jawbusFinisherHealth) return
        trigger(healthPercent.roundToInt())
    }

    fun reset() {
        trackedLevel = null
        resetActive()
        alertUntil = 0L
    }

    fun debugPreview() {
        val names = finisherNames()
        showAlert(ModConfig.values.jawbusFinisherHealth, names)
        Chat.component(
            Component.literal("Preview: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(configuredMessage(ModConfig.values.jawbusFinisherHealth, names)))
                .withStyle(ChatFormatting.WHITE),
        )
    }

    fun lastHookedAt(): Long = ModConfig.values.lastJawbusHookedAt

    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor) {
        if (System.currentTimeMillis() >= alertUntil) return
        val minecraft = Minecraft.getInstance()
        val width = 236
        val height = 42
        val x = (graphics.guiWidth() - width) / 2
        val y = 64
        graphics.fill(x, y, x + width, y + height, 0xE6231329.toInt())
        graphics.fill(x, y, x + width, y + 2, UiStyle.accent)
        graphics.fill(x, y + height - 2, x + width, y + height, UiStyle.accent)
        graphics.centeredText(minecraft.font, "JAWBUS FINISHER | $alertHealth%", x + width / 2, y + 8, 0xFFFFD7E8.toInt())
        graphics.centeredText(minecraft.font, "L5: $alertPlayers", x + width / 2, y + 24, 0xFFB9FFCF.toInt())
    }

    internal fun parseHealth(text: String): HealthSample? {
        val match = healthText.findAll(text).lastOrNull() ?: return null
        val current = magnitude(match.groupValues[1], match.groupValues[2]) ?: return null
        val maximum = match.groupValues[3].takeIf(String::isNotBlank)?.let {
            magnitude(it, match.groupValues[4])
        }
        return HealthSample(current, maximum)
    }

    private fun jawbusHealth(entity: Entity): HealthSample? {
        val name = entity.customName?.string ?: entity.name.string
        if (!name.contains("Lord Jawbus", ignoreCase = true)) return null
        parseHealth(name)?.let { return it }
        if (entity is LivingEntity && entity.maxHealth > 0f) {
            return HealthSample(entity.health.toDouble().coerceAtLeast(0.0), entity.maxHealth.toDouble())
        }
        return null
    }

    private fun trigger(healthPercent: Int) {
        triggered = true
        val names = finisherNames()
        showAlert(healthPercent, names)
        if (!ModConfig.values.jawbusFinisherPartyMessage || !PartyState.isInParty()) return
        val message = configuredMessage(healthPercent, names)
        runCatching { Minecraft.getInstance().connection?.sendCommand("pc $message") }
            .onFailure { MommyMods.logger.warn("Could not send the Jawbus finisher callout", it) }
    }

    private fun showAlert(healthPercent: Int, names: List<String>) {
        alertHealth = healthPercent
        alertPlayers = names.joinToString(", ").ifBlank { "none confirmed" }
        alertUntil = System.currentTimeMillis() + DISPLAY_MILLIS
    }

    private fun finisherNames(): List<String> {
        val minecraft = Minecraft.getInstance()
        val localName = minecraft.user.name
        val names = FishingPartyHelper.finisherNames().toMutableSet()
        if (LootingVScanner.localInventory(minecraft)?.hasLootingV == true) names += localName
        return names.sortedWith(compareBy<String> { !it.equals(localName, ignoreCase = true) }.thenBy { it.lowercase() })
    }

    private fun configuredMessage(healthPercent: Int, names: List<String>): String {
        val players = names.joinToString(", ").ifBlank { "none confirmed" }
        return ModConfig.values.jawbusFinisherMessage
            .ifBlank { DEFAULT_FINISHER_MESSAGE }
            .replace("{health}", healthPercent.toString())
            .replace("{players}", players)
            .replace(Regex("[\\r\\n]+"), " ")
            .trim()
            .take(MAX_MESSAGE_LENGTH)
    }

    private fun magnitude(number: String, suffix: String): Double? {
        val multiplier = when (suffix.lowercase()) {
            "k" -> 1_000.0
            "m" -> 1_000_000.0
            "b" -> 1_000_000_000.0
            "t" -> 1_000_000_000_000.0
            "" -> 1.0
            else -> return null
        }
        return number.toDoubleOrNull()?.times(multiplier)
    }

    private fun resetActive() {
        spawnAt = 0L
        maximumObserved = 0.0
        triggered = false
    }
}
