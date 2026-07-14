package com.zytrm.mommymods.feature

import com.zytrm.mommymods.model.FishingReadiness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyCommandsTest {
    @Test
    fun formatsHeaderAndOnePartyMessagePerPlayer() {
        val messages = LootingVPartyCheck.formatMessages(
            listOf(
                LootingVPartyCheck.Result("Zytrm", listOf("Hyperion", "Flaming Flay")),
                LootingVPartyCheck.Result("Fisher", emptyList()),
                LootingVPartyCheck.Result("Hidden", null),
            ),
        )

        assertEquals(
            listOf(
                "[MM] Who has Looting V:",
                "-Zytrm: Yes (Hyperion & Flaming Flay)",
                "-Fisher: No",
                "-Hidden: Unknown",
            ),
            messages,
        )
        assertTrue(messages.all { it.length <= 252 })
        assertTrue(messages.none { it.contains('\n') })
    }

    @Test
    fun recognizesOnlyLootingFiveOnSupportedWeapons() {
        assertEquals("Hyperion" to true, LootingVScanner.classify("Heroic Hyperion\nLooting V"))
        assertEquals("Hyperion" to true, LootingVScanner.classify("\u00a75Heroic Hyperion\n\u00a79Looting 5"))
        assertEquals("Flaming Flay" to false, LootingVScanner.classify("Flaming Flay\nLooting IV"))
        assertEquals(null, LootingVScanner.classify("Terminator\nLooting V"))
    }

    @Test
    fun combinesBothSupportedWeaponsAcrossTheFullScan() {
        val result = LootingVScanner.scan(
            listOf(
                "Hyperion" to true,
                "Flaming Flay" to false,
                "Flaming Flay" to true,
            ),
        )

        assertEquals(listOf("Hyperion", "Flaming Flay"), result.validWeapons)
        assertEquals(listOf("Hyperion", "Flaming Flay"), result.lootingVWeapons)
        assertTrue(result.hasLootingV)
    }

    @Test
    fun normalizesRemoteReadinessThroughTheSharedDetector() {
        val readiness = FishingReadiness(
            name = "Fisher",
            profileName = "Lemon",
            fishingLevel = 60,
            silverTrophyHunter = true,
            inventoryAvailable = true,
            lootingWeapon = "Flaming Flay",
            lootingWeapons = listOf("Hyperion", "Flaming Flay", "Hyperion"),
            lootingV = true,
            bloodshotBelt = true,
        )

        assertEquals(listOf("Hyperion", "Flaming Flay"), LootingVScanner.readinessWeapons(readiness))
        assertEquals(emptyList(), LootingVScanner.readinessWeapons(readiness.copy(lootingV = false)))
        assertEquals(null, LootingVScanner.readinessWeapons(readiness.copy(lootingV = null)))
    }

    @Test
    fun keepsEveryPartyMemberOnTheirOwnBoundedLine() {
        val results = (1..8).map { index ->
            LootingVPartyCheck.Result("LongPlayerName$index", listOf("Hyperion", "Flaming Flay"))
        }

        val messages = LootingVPartyCheck.formatMessages(results)
        assertEquals(9, messages.size)
        assertTrue(messages.all { it.length <= 252 })
        assertTrue(messages.last().contains("LongPlayerName8"))
        assertTrue(messages.last().contains("Hyperion"))
    }

    @Test
    fun eventDrivenPartySnapshotKeepsDisplayNames() {
        PartyState.reset()
        PartyState.applyMessage("You have created a party!", "LocalPlayer")
        PartyState.applyMessage("[VIP] Friend_One joined the party.", "LocalPlayer")

        val snapshot = PartyState.snapshot()
        assertTrue(snapshot.inParty)
        assertEquals("LocalPlayer", snapshot.leader)
        assertEquals(setOf("LocalPlayer", "Friend_One"), snapshot.members.toSet())
        PartyState.reset()
    }

    @Test
    fun partyFinderAndPartyChatRecoverObservableMembers() {
        PartyState.reset()
        PartyState.applyMessage("Party Finder > Fisher joined the group! (Fishing Level 58)", "Zytrm")
        PartyState.applyMessage("Party > [MVP+] QuietMember: ready", "Zytrm")

        val snapshot = PartyState.snapshot()
        assertTrue(snapshot.inParty)
        assertEquals(setOf("Zytrm", "Fisher", "QuietMember"), snapshot.members.toSet())
        assertTrue(PartyState.isMember("Zytrm"))
        PartyState.reset()
    }

    @Test
    fun partyTransferAndLeaveUpdateState() {
        PartyState.reset()
        PartyState.applyMessage("You have joined Owner's party!", "Zytrm")
        PartyState.applyMessage("Fisher joined the party.", "Zytrm")
        PartyState.applyMessage("The party was transferred to Fisher because Owner left", "Zytrm")

        val snapshot = PartyState.snapshot()
        assertTrue(snapshot.inParty)
        assertEquals(setOf("Zytrm", "Fisher"), snapshot.members.toSet())
        assertEquals("Fisher", snapshot.leader)
        PartyState.reset()
    }

    @Test
    fun joinEventsFireOnceAndNotForOrdinaryPartyChat() {
        PartyState.reset()
        val joins = mutableListOf<String>()
        val subscription = PartyState.addListener { event ->
            if (event is PartyState.Event.MemberJoined) joins += event.name
        }
        PartyState.applyMessage("You have created a party!", "LocalPlayer")
        PartyState.applyMessage("Friend_One joined the party.", "LocalPlayer")
        PartyState.applyMessage("Friend_One joined the party.", "LocalPlayer")
        PartyState.applyMessage("Party > QuietMember: hello", "LocalPlayer")

        assertEquals(listOf("Friend_One"), joins)
        subscription.close()
        PartyState.reset()
    }

    @Test
    fun kicksLeavesAndDisbandsRemoveObservedMembers() {
        PartyState.reset()
        PartyState.applyMessage("You have created a party!", "LocalPlayer")
        PartyState.applyMessage("Friend_One joined the party.", "LocalPlayer")
        PartyState.applyMessage("Friend_One has been removed from the party.", "LocalPlayer")
        assertEquals(listOf("LocalPlayer"), PartyState.snapshot().members)

        PartyState.applyMessage("Friend_Two joined the party.", "LocalPlayer")
        PartyState.applyMessage("LocalPlayer has disbanded the party!", "LocalPlayer")
        assertTrue(!PartyState.snapshot().inParty)
        assertEquals(emptyList(), PartyState.snapshot().members)
        PartyState.reset()
    }

    @Test
    fun partyFinderQueueAndInvitesRecoverLeadershipWithoutARefresh() {
        PartyState.reset()
        PartyState.applyMessage("Party Finder > Your party has been queued in the dungeon finder!", "LocalPlayer")
        assertEquals("LocalPlayer", PartyState.snapshot().leader)

        PartyState.reset()
        PartyState.applyMessage("[MVP+] PartyLead invited Friend_One to the party! They have 60 seconds to accept.", "LocalPlayer")
        val snapshot = PartyState.snapshot()
        assertEquals("PartyLead", snapshot.leader)
        assertEquals(setOf("LocalPlayer", "PartyLead"), snapshot.members.toSet())
        PartyState.reset()
    }
}
