package com.zytrm.mommymods.feature

import com.zytrm.mommymods.MommyMods
import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.config.PartyCommandSetting
import com.zytrm.mommymods.core.Chat
import com.zytrm.mommymods.core.GameContext
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
        PartyCommandDefinition(
            id = "jawbus_time",
            label = "Last Jawbus Time",
            defaultAlias = "jawbustime",
            action = JawbusTimePartyCommand::request,
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

    fun shouldHideInternalPartyRefresh(message: String): Boolean =
        LootingVPartyCheck.isAwaitingPartyRefresh() && PartyState.isPartyListResponse(message)

    fun reset() {
        LootingVPartyCheck.reset()
    }

    private fun normalizedAlias(value: String, fallback: String): String {
        val candidate = value.trim().removePrefix("/").lowercase()
        return candidate.takeIf { validAlias.matches(it) && it !in reservedAliases } ?: fallback
    }
}

object JawbusTimePartyCommand {
    private const val MAX_PARTY_MESSAGE_LENGTH = 252

    fun request() {
        if (!GameContext.isOnHypixel()) {
            Chat.info("Party Commands only runs on Hypixel.")
            return
        }
        val hookedAt = JawbusFinisherHelper.lastHookedAt()
        val result = if (hookedAt <= 0L) {
            "[MM] No recorded Jawbus hook yet."
        } else {
            val elapsed = (System.currentTimeMillis() - hookedAt).coerceAtLeast(0L)
            "[MM] Last Jawbus hooked ${formatElapsed(elapsed)} ago."
        }.take(MAX_PARTY_MESSAGE_LENGTH)

        if (!PartyState.isInParty()) {
            Chat.info(result.removePrefix("[MM] ") + " (not in a party)")
            return
        }
        runCatching { Minecraft.getInstance().connection?.sendCommand("pc $result") }
            .onFailure { MommyMods.logger.warn("Could not send the last Jawbus time", it) }
    }

    internal fun formatElapsed(milliseconds: Long): String {
        var seconds = milliseconds.coerceAtLeast(0L) / 1_000L
        val days = seconds / 86_400L
        seconds %= 86_400L
        val hours = seconds / 3_600L
        seconds %= 3_600L
        val minutes = seconds / 60L
        seconds %= 60L
        return buildList {
            if (days > 0L) add("${days}d")
            if (hours > 0L || days > 0L) add("${hours}h")
            if (minutes > 0L || hours > 0L || days > 0L) add("${minutes}m")
            add("${seconds}s")
        }.joinToString(" ")
    }
}

object LootingVPartyCheck {
    private const val PARTY_LIST_TIMEOUT_MILLIS = 3_500L
    private const val LOOKUP_TIMEOUT_MILLIS = 18_000L
    private const val MAX_PARTY_MESSAGE_LENGTH = 252
    private const val PARTY_LINE_INTERVAL_MILLIS = 550L
    private const val MESSAGE_HEADER = "[MM] Who has Looting V:"

    internal data class Result(val name: String, val weapons: List<String>?)

    private enum class Phase {
        REFRESHING_PARTY,
        CHECKING_MEMBERS,
    }

    private data class PendingResult(
        val name: String,
        val future: CompletableFuture<Result>,
    )

    private data class ActiveCheck(
        val token: Long,
        val localResult: Result,
        val startingListGeneration: Long,
        val baselineMembers: List<String>,
        val baselineInParty: Boolean,
        var phase: Phase,
        var deadline: Long,
        var sendToParty: Boolean = false,
        var fallbackNote: String? = null,
        var pendingResults: List<PendingResult> = emptyList(),
    )

    private data class PendingPartyOutput(
        val lines: ArrayDeque<String>,
        val results: List<Result>,
        var nextSendAt: Long,
    )

    private var activeCheck: ActiveCheck? = null
    private var pendingPartyOutput: PendingPartyOutput? = null
    private var nextToken = 0L

    fun request() {
        if (!GameContext.isOnHypixel()) {
            Chat.info("Party Commands only runs on Hypixel.")
            return
        }

        val minecraft = Minecraft.getInstance()
        pendingPartyOutput = null
        val localName = minecraft.user.name
        val localResult = Result(localName, LootingVScanner.localInventory(minecraft)?.lootingVWeapons)
        val snapshot = PartyState.snapshot()
        val check = ActiveCheck(
            token = ++nextToken,
            localResult = localResult,
            startingListGeneration = snapshot.listGeneration,
            baselineMembers = snapshot.members,
            baselineInParty = snapshot.inParty,
            phase = Phase.REFRESHING_PARTY,
            deadline = System.currentTimeMillis() + PARTY_LIST_TIMEOUT_MILLIS,
        )

        activeCheck = check
        if (canUseCachedParty(snapshot)) {
            beginMemberChecks(check, snapshot.members, sendToParty = true)
            return
        }

        if (minecraft.connection == null) {
            finishLocal(check, "not connected")
            return
        }
        if (!PartyState.requestRefresh(minecraft, force = true)) {
            beginMemberChecks(
                check,
                snapshot.members,
                sendToParty = snapshot.inParty,
                fallbackNote = "party refresh unavailable",
            )
        }
    }

    fun onTick() {
        val now = System.currentTimeMillis()
        activeCheck?.let { check ->
            when (check.phase) {
                Phase.REFRESHING_PARTY -> tickPartyRefresh(check, now)
                Phase.CHECKING_MEMBERS -> if (now >= check.deadline) finishMemberChecks(check.token)
            }
        }
        tickPartyOutput(now)
    }

    fun reset() {
        activeCheck = null
        pendingPartyOutput = null
        nextToken++
    }

    fun isAwaitingPartyRefresh(): Boolean = activeCheck?.phase == Phase.REFRESHING_PARTY

    internal fun canUseCachedParty(snapshot: PartyState.Snapshot): Boolean =
        snapshot.inParty && snapshot.listComplete && snapshot.fresh && snapshot.members.isNotEmpty()

    private fun tickPartyRefresh(check: ActiveCheck, now: Long) {
        if (activeCheck?.token != check.token) return
        val snapshot = PartyState.snapshot()
        if (snapshot.listGeneration > check.startingListGeneration) {
            if (!snapshot.inParty) {
                finishLocal(check, "not in a party")
                return
            }
            if (snapshot.listComplete) {
                beginMemberChecks(check, snapshot.members, sendToParty = true)
                return
            }
        }
        if (now < check.deadline) return

        val hasCurrentParty = snapshot.inParty
        val members = when {
            snapshot.members.isNotEmpty() -> snapshot.members
            check.baselineMembers.isNotEmpty() -> check.baselineMembers
            else -> emptyList()
        }
        beginMemberChecks(
            check,
            members,
            sendToParty = hasCurrentParty || check.baselineInParty,
            fallbackNote = if (hasCurrentParty || check.baselineInParty) null else "party refresh failed",
        )
    }

    private fun beginMemberChecks(
        check: ActiveCheck,
        members: List<String>,
        sendToParty: Boolean,
        fallbackNote: String? = null,
    ) {
        if (activeCheck?.token != check.token) return
        val minecraft = Minecraft.getInstance()
        val localName = check.localResult.name
        val uniqueMembers = linkedMapOf<String, String>()
        uniqueMembers[localName.lowercase()] = localName
        members.forEach { name -> uniqueMembers.putIfAbsent(name.lowercase(), name) }
        val ordered = uniqueMembers.values.sortedWith(
            compareBy<String> { !it.equals(localName, ignoreCase = true) }.thenBy { it.lowercase() },
        )

        check.phase = Phase.CHECKING_MEMBERS
        check.deadline = System.currentTimeMillis() + LOOKUP_TIMEOUT_MILLIS
        check.sendToParty = sendToParty
        check.fallbackNote = fallbackNote
        check.pendingResults = ordered.map { name ->
            val future = if (name.equals(localName, ignoreCase = true)) {
                CompletableFuture.completedFuture(check.localResult)
            } else {
                HypixelProfileClient.inspect(name, bypassCache = true)
                    .handle { readiness, throwable ->
                        if (throwable == null) Result(name, LootingVScanner.readinessWeapons(readiness)) else Result(name, null)
                    }
            }
            PendingResult(name, future)
        }

        CompletableFuture.allOf(*check.pendingResults.map { it.future }.toTypedArray()).whenComplete { _, _ ->
            minecraft.execute { finishMemberChecks(check.token) }
        }
    }

    private fun finishMemberChecks(token: Long) {
        val check = activeCheck?.takeIf { it.token == token } ?: return
        activeCheck = null
        val results = check.pendingResults.map { pending ->
            pending.future.getNow(Result(pending.name, null))
        }.ifEmpty { listOf(check.localResult) }

        val checkedNames = results.map { it.name.lowercase() }.toSet()
        val currentNames = PartyState.memberNames().map { it.lowercase() }.toSet()
        val partyChanged = check.sendToParty && PartyState.isInParty() &&
            currentNames.isNotEmpty() && currentNames != checkedNames
        if (check.sendToParty && PartyState.isInParty() && !partyChanged) {
            sendPartyResult(results, check)
        } else {
            showLocalResult(
                results,
                check.fallbackNote ?: if (partyChanged) "party changed" else "local result",
            )
        }
    }

    private fun sendPartyResult(results: List<Result>, check: ActiveCheck) {
        val connection = Minecraft.getInstance().connection
        if (connection == null) {
            showLocalResult(results, check.fallbackNote ?: "not connected")
            return
        }
        pendingPartyOutput = PendingPartyOutput(
            lines = ArrayDeque(formatMessages(results)),
            results = results,
            nextSendAt = 0L,
        )
        tickPartyOutput(System.currentTimeMillis())
    }

    private fun tickPartyOutput(now: Long) {
        val output = pendingPartyOutput ?: return
        if (now < output.nextSendAt) return
        if (!PartyState.isInParty()) {
            pendingPartyOutput = null
            showLocalResult(output.results, "party changed")
            return
        }
        val connection = Minecraft.getInstance().connection
        if (connection == null) {
            pendingPartyOutput = null
            showLocalResult(output.results, "not connected")
            return
        }

        val line = output.lines.removeFirst()
        runCatching { connection.sendCommand("pc $line") }
            .onSuccess {
                if (output.lines.isEmpty()) {
                    pendingPartyOutput = null
                } else {
                    output.nextSendAt = now + PARTY_LINE_INTERVAL_MILLIS
                }
            }
            .onFailure {
                pendingPartyOutput = null
                MommyMods.logger.warn("Could not send the Looting V party result", it)
                showLocalResult(output.results, "party send failed")
            }
    }

    private fun finishLocal(check: ActiveCheck, note: String) {
        if (activeCheck?.token != check.token) return
        activeCheck = null
        showLocalResult(listOf(check.localResult), note)
    }

    private fun showLocalResult(results: List<Result>, note: String) {
        val body = "Who has Looting V: " + results.joinToString(" ") { "-${formatEntry(it)}" }
        Chat.info("$body · $note")
    }

    internal fun formatMessages(results: List<Result>): List<String> = buildList {
        add(MESSAGE_HEADER)
        results.forEach { result ->
            add("-${formatEntry(result)}".take(MAX_PARTY_MESSAGE_LENGTH))
        }
    }

    private fun formatEntry(result: Result): String {
        val status = when {
            result.weapons == null -> "Unknown"
            result.weapons.isEmpty() -> "No"
            else -> "Yes (${result.weapons.joinToString(" & ")})"
        }
        return "${result.name}: $status"
    }
}
