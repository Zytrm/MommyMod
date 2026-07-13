package com.zytrm.mommymods.feature

import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.core.Chat
import com.zytrm.mommymods.core.GameContext
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.ClientLevel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object RareDropScreenshot {
    internal enum class Trigger { RNG_DROP, DYE_OR_VIAL, RARE_REWARD }

    private val personalRngAnnouncement = Regex(
        "^(?:CRAZY RARE DROP|PRAY RNGESUS DROP|INSANE DROP|ULTRA RARE DROP|VERY RARE DROP)!\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val rewardAnnouncement = Regex(
        "^(?:ULTRA RARE REWARD|RARE REWARD|SUPERPAIRS REWARD)!\\s*\\S",
        RegexOption.IGNORE_CASE,
    )
    private val namedRngAnnouncement = Regex("^RNG DROP!\\s*", RegexOption.IGNORE_CASE)
    private val personalRareDropAnnouncement = Regex("^RARE DROP!\\s*", RegexOption.IGNORE_CASE)
    private val rareRewardItems = Regex(
        "\\b(?:Metaphysical Serum|Experiment the Fish|Chimera(?: I)?|Radioactive Vial|Warden Heart|" +
            "Judgement Core|Overflux Capacitor|Necron's Handle|Phoenix Pet|Ender Dragon Pet)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val dyeOrVial = Regex("\\b(?:Radioactive Vial|[A-Za-z][A-Za-z' -]{0,30} Dye)\\b", RegexOption.IGNORE_CASE)
    private val directLocalOwnership = Regex(
        "^You\\s+(?:just\\s+)?(?:found|dropped|obtained|received|uncovered|claimed)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val namedOwnership = Regex(
        "^(?:[^!\\r\\n]{1,40}!\\s*)?(?:\\[[^]\\r\\n]+]\\s*)*([A-Za-z0-9_]{1,16})\\s+" +
            "(?:just\\s+)?(?:found|dropped|obtained|received|uncovered|claimed)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val playerChat = Regex(
        "^(?:(?:Party|Guild|Officer|Co-op|From|To|Friend)\\s*>|(?:\\[[^]\\r\\n]+]\\s*)*[A-Za-z0-9_]{1,16}\\s*:)",
        RegexOption.IGNORE_CASE,
    )
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")
    private const val CAPTURE_DELAY_TICKS = 3L
    private const val DUPLICATE_MILLIS = 8_000L

    private var clientTick = 0L
    private var captureAtTick = Long.MAX_VALUE
    private var pendingLabel = "rare-drop"
    private var scheduledLevel: ClientLevel? = null
    private var lastMessage = ""
    private var lastScheduledAt = 0L

    fun onMessage(message: String) {
        val settings = ModConfig.values
        if (!settings.rareDropScreenshots || !GameContext.isOnHypixel()) return
        val minecraft = Minecraft.getInstance()
        val trigger = classify(message, minecraft.user.name) ?: return
        if (trigger == Trigger.RNG_DROP && !settings.screenshotRngDrops) return
        if (trigger == Trigger.DYE_OR_VIAL && !settings.screenshotDyesAndVials) return
        if (trigger == Trigger.RARE_REWARD && !settings.screenshotRareRewards) return

        val now = System.currentTimeMillis()
        if (message == lastMessage && now - lastScheduledAt < DUPLICATE_MILLIS) return
        lastMessage = message
        lastScheduledAt = now
        pendingLabel = when (trigger) {
            Trigger.RNG_DROP -> "rng-drop"
            Trigger.DYE_OR_VIAL -> "dye-or-vial"
            Trigger.RARE_REWARD -> "rare-reward"
        }
        scheduledLevel = minecraft.level
        captureAtTick = clientTick + CAPTURE_DELAY_TICKS
    }

    fun onTick(minecraft: Minecraft) {
        clientTick++
        if (clientTick < captureAtTick) return
        if (!ModConfig.values.rareDropScreenshots || minecraft.level == null ||
            minecraft.level !== scheduledLevel || !GameContext.isOnHypixel()
        ) {
            clearPending()
            return
        }
        if (minecraft.screen != null && minecraft.screen !is AbstractContainerScreen<*>) {
            clearPending()
            return
        }
        val label = pendingLabel
        clearPending()
        val filename = "mommymods-$label-${LocalDateTime.now().format(dateFormat)}.png"
        Screenshot.grab(minecraft.gameDirectory, filename, minecraft.mainRenderTarget, 1) { result ->
            minecraft.execute { Chat.component(result) }
        }
    }

    fun reset() {
        clearPending()
        lastMessage = ""
        lastScheduledAt = 0L
    }

    internal fun classify(message: String, localName: String): Trigger? {
        if (localName.isBlank()) return null
        return message.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { classifyLine(it, localName) }
            .firstOrNull()
    }

    private fun classifyLine(text: String, localName: String): Trigger? {
        if (playerChat.containsMatchIn(text)) return null

        val containsDyeOrVial = dyeOrVial.containsMatchIn(text)
        val rngMatch = personalRngAnnouncement.find(text)
        if (rngMatch != null) {
            val body = text.substring(rngMatch.range.last + 1).trim()
            val owner = namedOwnership.find(body)?.groupValues?.get(1)
            if (owner != null && !owner.equals(localName, true)) return null
            return if (containsDyeOrVial) Trigger.DYE_OR_VIAL else Trigger.RNG_DROP
        }

        val namedRngMatch = namedRngAnnouncement.find(text)
        if (namedRngMatch != null) {
            val body = text.substring(namedRngMatch.range.last + 1).trim()
            val owner = namedOwnership.find(body)?.groupValues?.get(1) ?: return null
            if (!owner.equals(localName, true)) return null
            return if (containsDyeOrVial) Trigger.DYE_OR_VIAL else Trigger.RNG_DROP
        }

        val rareDropMatch = personalRareDropAnnouncement.find(text)
        if (rareDropMatch != null) {
            val body = text.substring(rareDropMatch.range.last + 1).trim()
            val owner = namedOwnership.find(body)?.groupValues?.get(1)
            if (owner != null && !owner.equals(localName, true)) return null
            if (containsDyeOrVial) return Trigger.DYE_OR_VIAL
            if (rareRewardItems.containsMatchIn(text)) return Trigger.RARE_REWARD
            return null
        }

        if (rewardAnnouncement.containsMatchIn(text)) {
            return if (containsDyeOrVial) Trigger.DYE_OR_VIAL else Trigger.RARE_REWARD
        }

        val isDirectlyLocal = directLocalOwnership.containsMatchIn(text)
        val namedOwner = namedOwnership.find(text)?.groupValues?.get(1)
        val isNamedLocal = namedOwner?.equals(localName, true) == true
        if (!isDirectlyLocal && !isNamedLocal) return null

        if (containsDyeOrVial) return Trigger.DYE_OR_VIAL
        if (rareRewardItems.containsMatchIn(text)) return Trigger.RARE_REWARD
        return null
    }

    private fun clearPending() {
        captureAtTick = Long.MAX_VALUE
        pendingLabel = "rare-drop"
        scheduledLevel = null
    }
}
