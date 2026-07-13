package com.zytrm.mommymods.feature

import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentHashMap

object PartyState {
    private val members = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var leader: String? = null

    @Volatile
    private var inParty = false

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
    private val partyHeader = Regex("^Party Members \\((\\d+)\\)$")
    private val partyRoleLine = Regex("^Party (Leader|Moderators?|Members?): (.+)$")
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

    internal fun applyMessage(message: String, localPlayer: String?) {
        if (disbanded.any { it.matches(message) }) {
            reset()
            return
        }

        partyHeader.matchEntire(message)?.let { match ->
            members.clear()
            leader = null
            inParty = match.groupValues[1].toIntOrNull()?.let { it > 0 } == true
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

        joinedOther.matchEntire(message)?.groupValues?.get(1)?.let { name ->
            inParty = true
            addMember(name)
            addLocalPlayer(localPlayer)
            return
        }
        partyChat.matchEntire(message)?.groupValues?.get(1)?.let { name ->
            inParty = true
            addMember(name)
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
            leader = localPlayer
            addLocalPlayer(localPlayer)
            return
        }

        removed.firstNotNullOfOrNull { it.matchEntire(message)?.groupValues?.get(1) }?.let { name ->
            members.remove(name.lowercase())
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
            addMember(newLeader)
            addLocalPlayer(localPlayer)
        }
    }

    fun isMember(name: String): Boolean = inParty && name.lowercase() in members

    fun isLocalLeader(): Boolean = inParty && leader == localName()

    fun memberNames(): Set<String> = members.toSet()

    fun reset() {
        members.clear()
        leader = null
        inParty = false
    }

    private fun addMember(name: String) {
        members.add(name.lowercase())
    }

    private fun addLocalPlayer(localPlayer: String?) {
        localPlayer?.lowercase()?.let(members::add)
    }

    private fun localName(): String? = Minecraft.getInstance().user.name.takeIf { it.isNotBlank() }?.lowercase()
}
