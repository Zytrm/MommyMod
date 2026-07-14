package com.zytrm.mommymods.feature

import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object PartyState {
    sealed interface Event {
        data class MemberJoined(val name: String) : Event
        data class MemberLeft(val name: String) : Event
        data class LeaderChanged(val name: String?) : Event
        data object Disbanded : Event
    }

    data class Snapshot(
        val inParty: Boolean,
        val members: List<String>,
        val leader: String?,
        val revision: Long,
        val updatedAt: Long,
    )

    private val members = ConcurrentHashMap.newKeySet<String>()
    private val displayNames = ConcurrentHashMap<String, String>()
    private val listeners = CopyOnWriteArrayList<(Event) -> Unit>()

    @Volatile
    private var leader: String? = null

    @Volatile
    private var inParty = false

    @Volatile
    private var revision = 0L

    @Volatile
    private var lastUpdatedAt = 0L

    private val joinedOther = Regex("^(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) joined the party\\.$")
    private val joinedSelf = Regex("^You have joined (?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16})(?:'s)? party!$")
    private val created = Regex("^You have created a party[!.]?$")
    private val queuedInFinder = Regex("^Party Finder > Your party has been queued in the dungeon finder!$")
    private val partyFinderJoin = Regex(
        "^Party Finder > (?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) joined (?:the dungeon group|the group)! \\(.*\\)$",
    )
    private val partyingWith = Regex("^You'll be partying with: (.+)$")
    private val partyChat = Regex("^Party > (?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}): .*$")
    private val invitation = Regex("^(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) invited .+ to the party!.*$")
    private val transferredBy = Regex(
        "^The party was transferred to (?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) by " +
            "(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16})\\.?$",
    )
    private val transferredAfterLeave = Regex(
        "^The party was transferred to (?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) because " +
            "(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) left\\.?$",
    )
    private val leaderDisconnected = Regex(
        "^The party leader, (?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) has disconnected, .+$",
    )
    private val leaderRejoined = Regex(
        "^The party leader (?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) has rejoined\\.$",
    )
    private val removed = listOf(
        Regex("^(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) (?:has left|has been removed from) the party\\.$"),
        Regex("^(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) was removed from your party because they disconnected\\.$"),
        Regex("^Kicked (?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16})(?: because they were offline| from the party)\\.$"),
        Regex("^You kicked (?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) from the party\\.$"),
        Regex("^(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) was kicked from the party by .+\\.$"),
    )
    private val disbanded = listOf(
        Regex("^(?:\\[[^]]+]\\s*)?[A-Za-z0-9_]{1,16} has disbanded the party!$"),
        Regex("^You have been kicked from the party.*$"),
        Regex("^The party was disbanded.*$"),
        Regex("^You left the party\\.$"),
        Regex("^You are not currently in a party\\.?$"),
    )
    private val listedName = Regex("(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16})")

    fun addListener(listener: (Event) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    fun onMessage(message: String) {
        applyMessage(message, localName())
    }

    internal fun applyMessage(message: String, localPlayer: String?) {
        if ('\n' in message || '\r' in message) {
            message.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { applyMessage(it, localPlayer) }
            return
        }
        val text = message.trim()
        if (text.isEmpty()) return

        if (disbanded.any { it.matches(text) }) {
            clearParty(notify = true)
            return
        }

        joinedSelf.matchEntire(text)?.groupValues?.get(1)?.let { owner ->
            clearParty(notify = false)
            inParty = true
            addMember(owner, notifyJoin = false)
            addLocalPlayer(localPlayer)
            setLeader(owner)
            markUpdated()
            return
        }

        if (created.matches(text)) {
            clearParty(notify = false)
            inParty = true
            addLocalPlayer(localPlayer)
            setLeader(localPlayer)
            markUpdated()
            return
        }

        if (queuedInFinder.matches(text)) {
            ensureParty(localPlayer)
            if (leader == null) setLeader(localPlayer)
            return
        }

        joinedOther.matchEntire(text)?.groupValues?.get(1)?.let { name ->
            ensureParty(localPlayer)
            addMember(name, notifyJoin = true)
            return
        }

        partyFinderJoin.matchEntire(text)?.groupValues?.get(1)?.let { name ->
            ensureParty(localPlayer)
            addMember(name, notifyJoin = true)
            return
        }

        partyingWith.matchEntire(text)?.groupValues?.get(1)?.let { roster ->
            ensureParty(localPlayer)
            roster.split(',').map(String::trim).forEach { segment ->
                listedName.matchEntire(segment)?.groupValues?.get(1)?.let { addMember(it, notifyJoin = true) }
            }
            return
        }

        removed.firstNotNullOfOrNull { it.matchEntire(text)?.groupValues?.get(1) }?.let { name ->
            removeMember(name, notifyLeave = true)
            if (leader.equals(name, ignoreCase = true)) setLeader(null)
            return
        }

        transferredBy.matchEntire(text)?.let { match ->
            ensureParty(localPlayer)
            addMember(match.groupValues[1], notifyJoin = false)
            addMember(match.groupValues[2], notifyJoin = false)
            setLeader(match.groupValues[1])
            return
        }

        transferredAfterLeave.matchEntire(text)?.let { match ->
            ensureParty(localPlayer)
            addMember(match.groupValues[1], notifyJoin = false)
            setLeader(match.groupValues[1])
            removeMember(match.groupValues[2], notifyLeave = true)
            return
        }

        leaderDisconnected.matchEntire(text)?.groupValues?.get(1)?.let { name ->
            ensureParty(localPlayer)
            addMember(name, notifyJoin = false)
            setLeader(name)
            return
        }

        leaderRejoined.matchEntire(text)?.groupValues?.get(1)?.let { name ->
            ensureParty(localPlayer)
            addMember(name, notifyJoin = false)
            setLeader(name)
            return
        }

        partyChat.matchEntire(text)?.groupValues?.get(1)?.let { name ->
            ensureParty(localPlayer)
            addMember(name, notifyJoin = false)
            return
        }

        invitation.matchEntire(text)?.groupValues?.get(1)?.let { inviter ->
            ensureParty(localPlayer)
            addMember(inviter, notifyJoin = false)
            if (leader == null) setLeader(inviter)
        }
    }

    fun isMember(name: String): Boolean = inParty && name.lowercase() in members

    fun isLocalLeader(): Boolean = inParty && leader == localName()?.lowercase()

    fun isInParty(): Boolean = inParty

    fun memberNames(): Set<String> = displayNames.values.toSet()

    fun snapshot(): Snapshot = Snapshot(
        inParty = inParty,
        members = displayNames.values.sortedWith(String.CASE_INSENSITIVE_ORDER),
        leader = leader?.let(displayNames::get),
        revision = revision,
        updatedAt = lastUpdatedAt,
    )

    fun reset() {
        clearParty(notify = false)
    }

    private fun ensureParty(localPlayer: String?) {
        if (!inParty) {
            inParty = true
            revision++
        }
        addLocalPlayer(localPlayer)
        markUpdated()
    }

    private fun addMember(name: String, notifyJoin: Boolean) {
        val key = name.lowercase()
        val added = members.add(key)
        displayNames[key] = name
        if (!added) return
        revision++
        markUpdated()
        if (notifyJoin) emit(Event.MemberJoined(name))
    }

    private fun removeMember(name: String, notifyLeave: Boolean) {
        val key = name.lowercase()
        if (!members.remove(key)) return
        val displayName = displayNames.remove(key) ?: name
        revision++
        markUpdated()
        if (notifyLeave) emit(Event.MemberLeft(displayName))
    }

    private fun addLocalPlayer(localPlayer: String?) {
        localPlayer?.takeIf(String::isNotBlank)?.let { addMember(it, notifyJoin = false) }
    }

    private fun setLeader(name: String?) {
        val normalized = name?.lowercase()
        if (leader == normalized) return
        leader = normalized
        revision++
        markUpdated()
        emit(Event.LeaderChanged(name))
    }

    private fun clearParty(notify: Boolean) {
        val hadParty = inParty || members.isNotEmpty()
        members.clear()
        displayNames.clear()
        leader = null
        inParty = false
        lastUpdatedAt = System.currentTimeMillis()
        revision++
        if (notify && hadParty) emit(Event.Disbanded)
    }

    private fun markUpdated() {
        lastUpdatedAt = System.currentTimeMillis()
    }

    private fun emit(event: Event) {
        listeners.forEach { listener -> runCatching { listener(event) } }
    }

    private fun localName(): String? = Minecraft.getInstance().user.name.takeIf(String::isNotBlank)
}
