package com.zytrm.mommymods.feature

import com.zytrm.mommymods.MommyMods
import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.config.PartyCommandSetting
import com.zytrm.mommymods.core.Chat
import com.zytrm.mommymods.core.GameContext
import com.zytrm.mommymods.model.FishingReadiness
import com.zytrm.mommymods.network.HypixelProfileClient
import net.minecraft.client.Minecraft
import java.util.concurrent.CompletableFuture

data class PartyCommandDefinition(
    val id: String,
    val label: String,
    val defaultAlias: String,
    val action: () -> Unit,
)

object PartyCommands {
    private val validAlias = Regex("^[a-z0-9_-]{1,24}$")
    private val reservedAliases = setOf(
        "mm", "mommymods", "mommy", "mmplay", "mmmedia", "mmcatchdebug", "mmpartydebug",
        "party", "p", "pc", "chat", "say", "msg", "tell", "w",
    )

    val definitions = listOf(
        PartyCommandDefinition(
            id = "looting_v_check",
            label = "Looting V Check",
            defaultAlias = "lootingv",
            action = LootingVPartyCheck::request,
        ),
    )

    fun ensureSettings() {
        val used = mutableSetOf<String>()
        definitions.forEach { definition ->
            val setting = setting(definition)
            var alias = normalizedAlias(setting.alias, definition.defaultAlias)
            var suffix = 2
            while (!used.add(alias)) {
                alias = "${definition.defaultAlias.take(21)}$suffix"
                suffix++
            }
            setting.alias = alias
        }
        ModConfig.save()
    }

    fun setting(definition: PartyCommandDefinition): PartyCommandSetting =
        ModConfig.partyCommandSetting(definition.id, definition.defaultAlias)

    fun alias(definition: PartyCommandDefinition): String =
        normalizedAlias(setting(definition).alias, definition.defaultAlias)

    fun acceptsAliasInput(value: String): Boolean = value.length <= 24 && value.all {
        it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-'
    }

    @JvmStatic
    fun tryHandleCommand(commandLine: String): Boolean {
        val normalized = commandLine.trim().replace(Regex("\\s+"), " ")
        val commandAlias = normalized.substringBefore(' ').lowercase()
        val definition = definitions.firstOrNull { alias(it) == commandAlias } ?: return false
        val arguments = normalized.substringAfter(' ', "").trim()
        Minecraft.getInstance().execute { execute(definition, arguments) }
        return true
    }

    fun execute(definition: PartyCommandDefinition, arguments: String = "") {
        if (arguments.isNotEmpty()) {
            Chat.info("Usage: /${alias(definition)}")
            return
        }
        if (!ModConfig.values.partyCommandsEnabled) {
            Chat.info("Party Commands is disabled.")
            return
        }
        if (!setting(definition).enabled) {
            Chat.info("${definition.label} is disabled.")
            return
        }
        definition.action()
    }

    fun onTick() {
        LootingVPartyCheck.onTick()
    }

    fun reset() {
        LootingVPartyCheck.reset()
    }

    private fun normalizedAlias(value: String, fallback: String): String {
        val candidate = value.trim().removePrefix("/").lowercase()
        return candidate.takeIf { validAlias.matches(it) && it !in reservedAliases } ?: fallback
    }
}

object LootingVPartyCheck {
    private const val PARTY_LIST_TIMEOUT_MILLIS = 6_000L
    private const val MAX_PARTY_MESSAGE_LENGTH = 252

    internal data class Result(val name: String, val weapons: List<String>?)

    private data class PendingPartyList(val generation: Long, val deadline: Long)

    private var pendingPartyList: PendingPartyList? = null
    private var lookupRunning = false
    private var lookupGeneration = 0L

    fun request() {
        if (!GameContext.isOnHypixel()) {
            Chat.info("Party Commands only runs on Hypixel.")
            return
        }
        if (pendingPartyList != null || lookupRunning) {
            Chat.info("Looting V Check is already running.")
            return
        }
        val connection = Minecraft.getInstance().connection ?: return
        val snapshot = PartyState.snapshot()
        pendingPartyList = PendingPartyList(
            generation = snapshot.listGeneration,
            deadline = System.currentTimeMillis() + PARTY_LIST_TIMEOUT_MILLIS,
        )
        connection.sendCommand("party list")
    }

    fun onTick() {
        val pending = pendingPartyList ?: return
        val snapshot = PartyState.snapshot()
        if (snapshot.listGeneration > pending.generation) {
            if (!snapshot.inParty) {
                pendingPartyList = null
                Chat.info("Join a party before using Looting V Check.")
                return
            }
            if (snapshot.listComplete) {
                pendingPartyList = null
                inspect(snapshot.members)
                return
            }
        }
        if (System.currentTimeMillis() >= pending.deadline) {
            pendingPartyList = null
            Chat.info("Could not refresh the party list.")
        }
    }

    fun reset() {
        pendingPartyList = null
        lookupRunning = false
        lookupGeneration++
    }

    private fun inspect(members: List<String>) {
        if (members.isEmpty()) {
            Chat.info("No party members were found.")
            return
        }
        lookupRunning = true
        val generation = ++lookupGeneration
        val minecraft = Minecraft.getInstance()
        val localName = minecraft.user.name
        val ordered = members.sortedWith(
            compareBy<String> { !it.equals(localName, ignoreCase = true) }.thenBy { it.lowercase() },
        )
        val futures = ordered.map { name ->
            if (name.equals(localName, ignoreCase = true)) {
                CompletableFuture.completedFuture(Result(name, LootingVScanner.localHotbar(minecraft)))
            } else {
                HypixelProfileClient.inspect(name, bypassCache = true, hotbarOnly = true)
                    .handle { readiness, throwable ->
                        if (throwable == null) Result(name, readiness.lootingWeapons()) else Result(name, null)
                    }
            }
        }
        CompletableFuture.allOf(*futures.toTypedArray()).whenComplete { _, _ ->
            minecraft.execute {
                if (generation != lookupGeneration) return@execute
                lookupRunning = false
                val currentMembers = PartyState.memberNames().map { it.lowercase() }.toSet()
                val checkedMembers = members.map { it.lowercase() }.toSet()
                if (!PartyState.isInParty() || currentMembers != checkedMembers) {
                    Chat.info("The Looting V result was cancelled because the party changed.")
                    return@execute
                }
                val definition = PartyCommands.definitions.first { it.id == "looting_v_check" }
                if (!ModConfig.values.partyCommandsEnabled || !PartyCommands.setting(definition).enabled) {
                    return@execute
                }
                val results = futures.map { it.getNow(Result("?", null)) }
                val message = formatMessage(results)
                val connection = minecraft.connection ?: return@execute
                runCatching { connection.sendCommand("pc $message") }
                    .onFailure { MommyMods.logger.warn("Could not send the Looting V party result", it) }
            }
        }
    }

    internal fun formatMessage(results: List<Result>): String {
        val detailed = formatAll(results, includeWeapons = true)
        if (detailed.length <= MAX_PARTY_MESSAGE_LENGTH) return detailed
        val compact = formatAll(results, includeWeapons = false)
        if (compact.length <= MAX_PARTY_MESSAGE_LENGTH) return compact
        return formatLimited(results)
    }

    private fun formatAll(results: List<Result>, includeWeapons: Boolean): String =
        MESSAGE_PREFIX + results.joinToString(", ") { result -> formatEntry(result, includeWeapons) }

    private fun formatLimited(results: List<Result>): String {
        val entries = results.map { formatEntry(it, includeWeapons = false) }
        val output = StringBuilder(MESSAGE_PREFIX)
        for (index in entries.indices) {
            val entry = entries[index]
            val separator = if (index == 0) "" else ", "
            val remaining = entries.size - index - 1
            val overflow = if (remaining > 0) ", +$remaining more" else ""
            if (output.length + separator.length + entry.length + overflow.length > MAX_PARTY_MESSAGE_LENGTH) {
                output.append(", +${remaining + 1} more")
                break
            }
            output.append(separator).append(entry)
        }
        return output.toString().take(MAX_PARTY_MESSAGE_LENGTH)
    }

    private fun formatEntry(result: Result, includeWeapons: Boolean): String {
        val status = when {
            result.weapons == null -> "Unknown"
            result.weapons.isEmpty() -> "No"
            includeWeapons -> "Yes (${result.weapons.joinToString(" & ")})"
            else -> "Yes"
        }
        return "${result.name}: $status"
    }

    private fun FishingReadiness.lootingWeapons(): List<String>? = when {
        hasLootingV == null -> null
        hasLootingV == false -> emptyList()
        lootingWeapons.isNotEmpty() -> lootingWeapons
        lootingWeapon != null -> listOf(lootingWeapon)
        else -> emptyList()
    }

    private const val MESSAGE_PREFIX = "[MM] Who has Looting V?: "
}
