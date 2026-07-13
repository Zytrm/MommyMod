package com.zytrm.mommymods.feature

import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.core.Chat
import com.zytrm.mommymods.core.GameContext
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object RareDropScreenshot {
    internal enum class Trigger { RNG_DROP, DYE_OR_VIAL, RARE_REWARD }

    private val rngAnnouncement = Regex(
        "^\\s*(?:CRAZY RARE DROP|PRAY RNGESUS DROP|INSANE DROP|ULTRA RARE DROP|VERY RARE DROP|RARE DROP)!",
        RegexOption.IGNORE_CASE,
    )
    private val rewardAnnouncement = Regex(
        "^\\s*(?:ULTRA RARE REWARD|RARE REWARD|SUPERPAIRS REWARD)!",
        RegexOption.IGNORE_CASE,
    )
    private val rareRewardItems = Regex(
        "\\b(?:Metaphysical Serum|Experiment the Fish|Chimera(?: I)?|Radioactive Vial|Warden Heart|" +
            "Judgement Core|Overflux Capacitor|Necron's Handle|Phoenix Pet|Ender Dragon Pet)\\b|\\b[A-Za-z' ]+ VII\\b",
        RegexOption.IGNORE_CASE,
    )
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")
    private const val CAPTURE_DELAY_TICKS = 3L
    private const val DUPLICATE_MILLIS = 8_000L

    private var clientTick = 0L
    private var captureAtTick = Long.MAX_VALUE
    private var pendingLabel = "rare-drop"
    private var lastMessage = ""
    private var lastScheduledAt = 0L

    fun onMessage(message: String) {
        val settings = ModConfig.values
        if (!settings.rareDropScreenshots || !GameContext.isOnHypixel()) return
        val trigger = classify(message, Minecraft.getInstance().user.name) ?: return
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
        captureAtTick = clientTick + CAPTURE_DELAY_TICKS
    }

    fun onTick(minecraft: Minecraft) {
        clientTick++
        if (clientTick < captureAtTick) return
        captureAtTick = Long.MAX_VALUE
        if (minecraft.level == null || !GameContext.isOnHypixel()) return
        val filename = "mommymods-$pendingLabel-${LocalDateTime.now().format(dateFormat)}.png"
        Screenshot.grab(minecraft.gameDirectory, filename, minecraft.mainRenderTarget, 1) { result ->
            minecraft.execute { Chat.component(result) }
        }
    }

    fun reset() {
        captureAtTick = Long.MAX_VALUE
        pendingLabel = "rare-drop"
        lastMessage = ""
        lastScheduledAt = 0L
    }

    internal fun classify(message: String, localName: String): Trigger? {
        val text = message.trim()
        if (text.startsWith("Party >", true) || text.startsWith("Guild >", true) ||
            text.startsWith("Co-op >", true) || text.startsWith("From ", true) || text.startsWith("To ", true)
        ) return null

        val localReference = Regex("\\b(?:You|${Regex.escape(localName)})\\b", RegexOption.IGNORE_CASE)
        val dropVerb = Regex("\\b(?:found|dropped|obtained|received|uncovered|claimed)\\b", RegexOption.IGNORE_CASE)
        val dyeOrVial = text.contains("Radioactive Vial", true) || Regex("\\b[A-Za-z' -]+ Dye\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        if (dyeOrVial && rngAnnouncement.containsMatchIn(text)) return Trigger.DYE_OR_VIAL
        if (dyeOrVial && localReference.containsMatchIn(text) &&
            (dropVerb.containsMatchIn(text) || rngAnnouncement.containsMatchIn(text))
        ) return Trigger.DYE_OR_VIAL

        if (rewardAnnouncement.containsMatchIn(text)) return Trigger.RARE_REWARD
        if (localReference.containsMatchIn(text) && dropVerb.containsMatchIn(text) && rareRewardItems.containsMatchIn(text)) {
            return if (dyeOrVial) Trigger.DYE_OR_VIAL else Trigger.RARE_REWARD
        }
        if (rngAnnouncement.containsMatchIn(text)) return Trigger.RNG_DROP
        return null
    }
}
