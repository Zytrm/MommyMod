package com.zytrm.mommymods.feature

import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentHashMap

object PartyState {
    private val members = ConcurrentHashMap.newKeySet<String>()
    private val displayNames = ConcurrentHashMap<String, String>()

    @Volatile
    private var leader: String? = null

    @Volatile
    private var inParty = false

    @Volatile
    private var partyListGeneration = 0L

    @Volatile
    private var expectedMemberCount: Int? = null

    data class Snapshot(
        val inParty: Boolean,
        val members: List<String>,
        val listGeneration: Long,
        val listComplete: Boolean,
    )

    private val joinedOther = Regex("^(?:\\[[^]]+]\\s+)?([A-Za-z0-9_]{3,16}) joined the party\\.$")
    private val joinedSelf = Regex("^You have joined (?:\\[[^]]+]\\s+)?([A-Za-z0-9_]{3,16})'?s? party!$")
    private val created = Regex("^You have created a party!?$")
    private val removed = listOf(
        Regex("^(?:\\[[^]]+]\\s+)?([A-Za-z0-9_]{3,16}) (?:has left|has been removed from) the party\\.$"),
        Regex("^(?:\\[[^]]+]\\s+)?([A-Za-z0-9_]{3,16}) was removed from your party because they disconnected\\.$"),
        Regex("^Kicked (?:\\[[^]]+]\\s+)?([A-Za-z0-9_]{3,16})(?: because they were offline| from the party)\\.$"),
        Regex("^You kicked (?:\\[[^]]+]\\s+)?([A-Za-z0-9_]{3,16}) from the party\\.$"),
        Regex("^(?:\\[[^]]+]\\s+)?([A-Za-z0-9_]{3,16}) was kicked from the party by .+\\.$"),
    )
    private val invitation = Regex("^(?:\\[[^]]+]\\s+)?([A-Za-z0-9_]{3,16}) invited .+ to the party!.*$")
    private val transferred = Regex("^The party was transferred to (?:\\[[^]]+]\\s+)?([A-Za-z0-9_]{3,16})(?: by .+| because .+)?$")
    private val partyHeader = Regex("^Party Members\\s*\\((\\d+)\\)$")
    private val partyRoleLine = Regex("^Party (Leader|Moderators?|Members?): (.+)$")
    private val compactPartyMember = Regex(
        "^[●○]\\s+(?:\\[[^]]+]\\s*)*([A-Za-z0-9_]{3,16})\\s+\\((Leader|Moderator|Member)\\)$",
    )
    private val partyChat = Regex("^Party > (?:\\[[^]]+]\\s+)?([A-Za-z0-9_]{3,16}): .*$")
    private val listedPlayer = Regex("(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{3,16})(?=\\s*(?:●|,|$))")
    private val disbanded = listOf(
        Regex("^You left the party\\.$"),
        Regex("^You are not currently in a party\\.?$"),
        Regex("^The party was disbanded.*$"),
        Regex("^You have been kicked from the party.*$"),
    )

    fun onMessage(message: String) {
        applyMessage(message, localName())
    }

    internal fun isPartyListResponse(message: String): Boolean = message.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .any { line ->
            partyHeader.matches(line) ||
                partyRoleLine.matches(line) ||
                compactPartyMember.matches(line) ||
                line.startsWith("[Warp] [Invite] [Disband]") ||
                line.matches(Regex("^You are not currently in a party\\.?$"))
        }

    internal fun applyMessage(message: String, localPlayer: String?) {
        if ('\n' in message || '\r' in message) {
            message.lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach { applyMessage(it, localPlayer) }
            return
        }
        val trimmed = message.trim()
        if (trimmed != message) {
            applyMessage(trimmed, localPlayer)
            return
        }
        if (disbanded.any { it.matches(message) }) {
            reset()
            return
        }

        partyHeader.matchEntire(message)?.let { match ->
            members.clear()
            displayNames.clear()
            leader = null
            expectedMemberCount = match.groupValues[1].toIntOrNull()
            inParty = expectedMemberCount?.let { it > 0 } == true
            partyListGeneration++
            addLocalPlayer(localPlayer)
            return
        }

        partyRoleLine.matchEntire(message)?.let { match ->
            inParty = true
            val role = match.groupValues[1]
            val names = listedPlayer.findAll(match.groupValues[2]).map { it.groupValues[1] }.toList()
            names.forEach(::addMember)
            if (role == "Leader") leader = names.firstOrNull()?.lowercase()
            addLocalPlayer(localPlayer)
            return
        }

        compactPartyMember.matchEntire(message)?.let { match ->
            inParty = true
            val name = match.groupValues[1]
            addMember(name)
            if (match.groupValues[2] == "Leader") leader = name.lowercase()
            addLocalPlayer(localPlayer)
            return
        }

        joinedOther.matchEntire(message)?.groupValues?.get(1)?.let { name ->
            inParty = true
            addKnownMember(name)
            addLocalPlayer(localPlayer)
            return
        }
        partyChat.matchEntire(message)?.groupValues?.get(1)?.let { name ->
            inParty = true
            addKnownMember(name)
            addLocalPlayer(localPlayer)
            return
        }
        joinedSelf.matchEntire(message)?.groupValues?.get(1)?.let { name ->
            reset()
            inParty = true
            leader = name.lowercase()
            addMember(name)
            addLocalPlayer(localPlayer)
            return
        }
        if (created.matches(message)) {
            reset()
            inParty = true
            leader = localPlayer?.lowercase()
            addLocalPlayer(localPlayer)
            expectedMemberCount = if (localPlayer.isNullOrBlank()) null else 1
            return
        }

        removed.firstNotNullOfOrNull { it.matchEntire(message)?.groupValues?.get(1) }?.let { name ->
            removeKnownMember(name)
            if (leader.equals(name, ignoreCase = true)) leader = null
            return
        }

        invitation.matchEntire(message)?.groupValues?.get(1)?.let { inviter ->
            inParty = true
            addMember(inviter)
            addLocalPlayer(localPlayer)
        }
        transferred.matchEntire(message)?.groupValues?.get(1)?.let { newLeader ->
            inParty = true
            leader = newLeader.lowercase()
            addKnownMember(newLeader)
            addLocalPlayer(localPlayer)
        }
    }

    fun isMember(name: String): Boolean = inParty && name.lowercase() in members

    fun isLocalLeader(): Boolean = inParty && leader == localName()?.lowercase()

    fun isInParty(): Boolean = inParty

    fun memberNames(): Set<String> = displayNames.values.toSet()

    fun snapshot(): Snapshot {
        val expected = expectedMemberCount
        val names = displayNames.values.sortedWith(String.CASE_INSENSITIVE_ORDER)
        return Snapshot(
            inParty = inParty,
            members = names,
            listGeneration = partyListGeneration,
            listComplete = inParty && expected != null && names.size >= expected,
        )
    }

    fun reset() {
        members.clear()
        displayNames.clear()
        leader = null
        inParty = false
        expectedMemberCount = null
        partyListGeneration++
    }

    private fun addMember(name: String) {
        val key = name.lowercase()
        members.add(key)
        displayNames[key] = name
    }

    private fun addKnownMember(name: String) {
        val wasKnown = members.contains(name.lowercase())
        addMember(name)
        if (!wasKnown) expectedMemberCount = expectedMemberCount?.plus(1)
    }

    private fun removeKnownMember(name: String) {
        val key = name.lowercase()
        if (members.remove(key)) {
            displayNames.remove(key)
            expectedMemberCount = expectedMemberCount?.minus(1)?.coerceAtLeast(0)
        }
    }

    private fun addLocalPlayer(localPlayer: String?) {
        localPlayer?.let(::addMember)
    }

    private fun localName(): String? = Minecraft.getInstance().user.name.takeIf { it.isNotBlank() }
}
