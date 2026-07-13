package com.zytrm.mommymods.feature

import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.core.Chat
import com.zytrm.mommymods.core.GameContext
import com.zytrm.mommymods.model.FishingReadiness
import com.zytrm.mommymods.network.HypixelProfileClient
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object FishingPartyHelper {
    private const val SCAN_TICKS = 160L
    private const val HUD_REFRESH_TICKS = 100L
    private const val HUD_STALE_MILLIS = 60 * 1000L
    private val validPlayerName = Regex("^[A-Za-z0-9_]{1,16}$")
    private val joinedParty = Regex("^(?:\\[[^]]+] )?(\\w{1,16}) joined the party\\.$")
    private val pendingApi = ConcurrentHashMap.newKeySet<String>()
    private val pendingScans = ConcurrentHashMap<String, PendingScan>()
    private val readinessCache = ConcurrentHashMap<String, CachedReadiness>()
    private var clientTick = 0L

    data class HudStatus(
        val name: String,
        val canJawbus: Boolean?,
        val lootingV: Boolean?,
        val bloodshot: Boolean?,
    )

    private data class CachedReadiness(
        val data: FishingReadiness,
        val updatedAt: Long,
    )

    private data class PendingScan(
        val name: String,
        val startedAt: Long,
        var observedInWorld: Boolean = false,
        val validWeapons: MutableSet<String> = linkedSetOf(),
        val lootingVWeapons: MutableSet<String> = linkedSetOf(),
    )

    fun onMessage(message: String) {
        if (!ModConfig.values.fishingPartyHelper || !GameContext.isOnHypixel()) return
        val name = joinedParty.matchEntire(message)?.groupValues?.get(1) ?: return
        if (name.equals(Minecraft.getInstance().user.name, ignoreCase = true)) return

        inspectProfile(name, announce = true)
    }

    fun onTick(minecraft: Minecraft) {
        clientTick++
        if (!ModConfig.values.fishingPartyHelper || !GameContext.isOnHypixel()) {
            pendingScans.clear()
            return
        }

        if (ModConfig.values.partyReadinessHud && clientTick % HUD_REFRESH_TICKS == 0L) {
            refreshPartyReadiness()
        }

        val level = minecraft.level ?: return
        pendingScans.entries.toList().forEach { (key, scan) ->
            findPlayer(level, scan.name)?.let { observe(minecraft, level, it, scan) }
            if (clientTick - scan.startedAt >= SCAN_TICKS) {
                pendingScans.remove(key)
                val readiness = scan.toReadiness()
                cache(readiness)
                show(readiness)
                maybeKick(readiness)
            }
        }
    }

    private fun inspectProfile(name: String, announce: Boolean, force: Boolean = false) {
        val key = name.lowercase()
        if (!pendingApi.add(key)) return
        HypixelProfileClient.inspect(name, bypassCache = force).whenComplete { readiness, throwable ->
            pendingApi.remove(key)
            Minecraft.getInstance().execute {
                if (throwable != null) {
                    if (announce) startInGameScan(name)
                } else {
                    val resolved = withLocalInventory(readiness)
                    cache(resolved)
                    if (announce) {
                        show(resolved)
                        maybeKick(resolved)
                    }
                }
            }
        }
    }

    fun refreshPartyReadiness(force: Boolean = false) {
        if (!ModConfig.values.fishingPartyHelper || !GameContext.isOnHypixel() || !PartyState.isInParty()) return
        val now = System.currentTimeMillis()
        PartyState.memberNames().forEach { name ->
            val cached = readinessCache[name.lowercase()]
            if (force || cached == null || now - cached.updatedAt >= HUD_STALE_MILLIS) {
                inspectProfile(name, announce = false, force = force)
            }
        }
    }

    fun hudStatuses(): List<HudStatus> {
        if (!ModConfig.values.fishingPartyHelper || !PartyState.isInParty()) return emptyList()
        val minecraft = Minecraft.getInstance()
        val localName = minecraft.user.name
        val localInventory = LootingVScanner.localInventory(minecraft)
        return PartyState.memberNames()
            .sortedWith(compareBy<String> { !it.equals(localName, ignoreCase = true) }.thenBy { it.lowercase() })
            .map { name ->
                val readiness = readinessCache[name.lowercase()]?.data
                HudStatus(
                    name = name,
                    canJawbus = readiness?.canJawbus,
                    lootingV = if (name.equals(localName, ignoreCase = true)) localInventory?.hasLootingV else readiness?.hasLootingV,
                    bloodshot = readiness?.bloodshotBelt,
                )
            }
    }

    fun finisherNames(): List<String> = hudStatuses().filter { it.lootingV == true }.map(HudStatus::name)

    fun reset() {
        pendingApi.clear()
        pendingScans.clear()
        readinessCache.clear()
        clientTick = 0L
    }

    fun debugInspect(name: String) {
        if (!validPlayerName.matches(name)) {
            Chat.info("Debug lookup rejected: player names must be 1-16 letters, numbers, or underscores.")
            return
        }
        val diagnostics = CopyOnWriteArrayList<HypixelProfileClient.Diagnostic>()
        HypixelProfileClient.inspect(
            name,
            diagnostics = { diagnostics.add(it) },
            bypassCache = true,
        ).whenComplete { readiness, throwable ->
            Minecraft.getInstance().execute {
                val path = diagnostics.joinToString(" > ") { "${it.stage}:${it.status}" }
                Chat.info("Debug $name: ${path.ifBlank { "no diagnostics" }}")
                if (throwable == null) {
                    show(readiness)
                    return@execute
                }

                val cause = generateSequence(throwable) { it.cause }.last()
                val classified = cause as? HypixelProfileClient.LookupException
                val kind = classified?.kind?.name ?: "UNCLASSIFIED"
                val stage = classified?.stage ?: "unknown"
                Chat.info("Debug failed: $kind at $stage — ${cause.message ?: "unknown error"}")
                showDebugFallback(name)
            }
        }
    }

    fun debugSelf() {
        debugInspect(Minecraft.getInstance().user.name)
    }

    fun debugStatus() {
        Chat.info(
            "Party helper status: enabled=${ModConfig.values.fishingPartyHelper}, " +
                "hypixel=${GameContext.isOnHypixel()}, pendingProfiles=${pendingApi.size}, " +
                "pendingGearScans=${pendingScans.size}, hudProfiles=${readinessCache.size}, ${HypixelProfileClient.statusSummary()}.",
        )
    }

    private fun showDebugFallback(name: String) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        val player = level?.let { findPlayer(it, name) }
        if (level == null || player == null) {
            Chat.info("Fallback unavailable: player not loaded.")
            return
        }

        val scan = PendingScan(name, clientTick)
        observe(minecraft, level, player, scan)
        Chat.info("Fallback: visible held/equipped gear.")
        show(scan.toReadiness())
    }

    private fun startInGameScan(name: String) {
        val key = name.lowercase()
        pendingScans.putIfAbsent(key, PendingScan(name, clientTick))
    }

    private fun findPlayer(level: ClientLevel, name: String): AbstractClientPlayer? =
        level.players().firstOrNull { it.scoreboardName.equals(name, ignoreCase = true) }

    private fun observe(
        minecraft: Minecraft,
        level: ClientLevel,
        player: AbstractClientPlayer,
        scan: PendingScan,
    ) {
        scan.observedInWorld = true
        visibleEquipment(player).filterNot { it.isEmpty }.forEach { stack ->
            LootingVScanner.classifyStack(minecraft, level, stack)?.let { (weapon, hasLootingV) ->
                scan.validWeapons += weapon
                if (hasLootingV) scan.lootingVWeapons += weapon
            }
        }
    }

    private fun visibleEquipment(player: AbstractClientPlayer): List<ItemStack> = listOf(
        player.mainHandItem,
        player.offhandItem,
        player.getItemBySlot(EquipmentSlot.HEAD),
        player.getItemBySlot(EquipmentSlot.CHEST),
        player.getItemBySlot(EquipmentSlot.LEGS),
        player.getItemBySlot(EquipmentSlot.FEET),
    )

    private fun PendingScan.toReadiness(): FishingReadiness {
        val hasSupportedWeapon = validWeapons.isNotEmpty()
        val enchantedWeapons = lootingVWeapons.toList()
        return FishingReadiness(
        name = name,
        profileName = "In-game",
        fishingLevel = null,
        silverTrophyHunter = null,
        inventoryAvailable = observedInWorld,
        lootingWeapon = enchantedWeapons.firstOrNull() ?: validWeapons.firstOrNull(),
        lootingWeapons = enchantedWeapons,
        lootingV = if (hasSupportedWeapon) enchantedWeapons.isNotEmpty() else null,
        beltCheckAvailable = false,
        fishingBelt = null,
        bloodshotBelt = null,
        observedInWorld = observedInWorld,
    )
    }

    private fun withLocalInventory(data: FishingReadiness): FishingReadiness {
        val minecraft = Minecraft.getInstance()
        if (!data.name.equals(minecraft.user.name, ignoreCase = true)) return data
        val inventory = LootingVScanner.localInventory(minecraft) ?: return data
        return data.copy(
            lootingWeapon = inventory.lootingVWeapons.firstOrNull() ?: inventory.validWeapons.firstOrNull(),
            lootingWeapons = inventory.lootingVWeapons,
            lootingV = inventory.hasLootingV,
        )
    }

    private fun cache(data: FishingReadiness) {
        readinessCache[data.name.lowercase()] = CachedReadiness(data, System.currentTimeMillis())
    }

    private data class SummaryValue(val label: String, val value: String, val state: CheckState)

    private fun show(data: FishingReadiness) {
        Chat.component(
            Component.literal(data.name).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(" · ${data.profileName}").withStyle(ChatFormatting.DARK_GRAY)),
        )
        Chat.component(
            summaryLine(
                SummaryValue(
                    "Fish 45+",
                    data.fishingLevel?.toString() ?: "?",
                    state(data.fishingLevel?.let { it >= 45 }),
                ),
                SummaryValue("Silver", yesNo(data.silverTrophyHunter), state(data.silverTrophyHunter)),
                SummaryValue("Jawbus", yesNo(data.canJawbus), state(data.canJawbus)),
            ),
        )

        val looting = when {
            data.hasLootingV == true -> "Yes (${LootingVScanner.readinessWeapons(data).orEmpty().joinToString(" & ")})"
            data.hasLootingV == false && data.lootingWeapon != null -> "No (${data.lootingWeapon})"
            data.hasLootingV == false -> "No weapon"
            data.observedInWorld -> "Not held"
            else -> "?"
        }
        Chat.component(
            summaryLine(
                SummaryValue("Looting V", looting, state(data.hasLootingV)),
                beltSummary(data),
            ),
        )
    }

    private fun beltSummary(data: FishingReadiness): SummaryValue = when {
        !data.beltCheckAvailable -> SummaryValue("Belt", "?", CheckState.UNKNOWN)
        data.fishingBelt == null -> SummaryValue("Belt", "Not worn", CheckState.FAIL)
        data.bloodshotBelt == true -> SummaryValue("Belt", "Bloodshot ${data.fishingBelt}", CheckState.PASS)
        data.bloodshotBelt == false -> SummaryValue("Belt", "${data.fishingBelt}, no Bloodshot", CheckState.FAIL)
        else -> SummaryValue("Belt", "?", CheckState.UNKNOWN)
    }

    private fun summaryLine(vararg values: SummaryValue): Component {
        val result = Component.empty()
        values.forEachIndexed { index, entry ->
            if (index > 0) result.append(Component.literal("  |  ").withStyle(ChatFormatting.DARK_GRAY))
            result.append(Component.literal("${entry.label}: ").withStyle(ChatFormatting.GRAY))
            result.append(Component.literal(entry.value).withStyle(when (entry.state) {
                CheckState.PASS -> ChatFormatting.GREEN
                CheckState.FAIL -> ChatFormatting.RED
                CheckState.UNKNOWN -> ChatFormatting.YELLOW
            }))
        }
        return result
    }

    private fun yesNo(value: Boolean?) = when (value) {
        true -> "Yes"
        false -> "No"
        null -> "?"
    }

    private fun state(value: Boolean?) = when (value) {
        true -> CheckState.PASS
        false -> CheckState.FAIL
        null -> CheckState.UNKNOWN
    }

    private fun maybeKick(data: FishingReadiness) {
        val settings = ModConfig.values
        if (!settings.autoKick || !PartyState.isLocalLeader() || !PartyState.isMember(data.name)) return

        val reasons = buildList {
            if (settings.kickNoLootingV && data.hasLootingV == false) add("No Looting V")
            if (settings.kickCantJawbus && data.canJawbus == false) add("Can't Jawbus")
        }
        if (reasons.isEmpty()) return

        Chat.info("Auto-kicking ${data.name}: ${reasons.joinToString(", ")}")
        Minecraft.getInstance().connection?.sendCommand("party kick ${data.name}")
    }

    private enum class CheckState { PASS, FAIL, UNKNOWN }
}
