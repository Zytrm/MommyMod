package com.zytrm.mommymods.feature

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
        assertEquals("Flaming Flay" to false, LootingVScanner.classify("Flaming Flay\nLooting IV"))
        assertEquals(null, LootingVScanner.classify("Terminator\nLooting V"))
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
    fun partyListSnapshotKeepsDisplayNamesAndCompletionState() {
        PartyState.reset()
        PartyState.applyMessage("Party Members (2)", "LocalPlayer")
        PartyState.applyMessage("Party Leader: [MVP+] LocalPlayer ●", "LocalPlayer")
        PartyState.applyMessage("Party Members: [VIP] Friend_One ●", "LocalPlayer")

        val snapshot = PartyState.snapshot()
        assertTrue(snapshot.inParty)
        assertTrue(snapshot.listComplete)
        assertEquals(setOf("LocalPlayer", "Friend_One"), snapshot.members.toSet())
        PartyState.reset()
    }

    @Test
    fun parsesCurrentCompactPartyListWithWhitespace() {
        PartyState.reset()
        PartyState.applyMessage("  Party Members (1)  ", "Zytrm")
        PartyState.applyMessage("● [MVP++] Zytrm (Leader)", "Zytrm")

        val snapshot = PartyState.snapshot()
        assertTrue(snapshot.inParty)
        assertTrue(snapshot.listComplete)
        assertEquals(listOf("Zytrm"), snapshot.members)
        assertTrue(PartyState.isMember("Zytrm"))
        PartyState.reset()
    }

    @Test
    fun parsesMultiLinePartyDisplayAsCompleteVisibleState() {
        PartyState.reset()
        PartyState.applyMessage(
            """
            Party Members (2)

            [Warp] [Invite] [Disband]
            ● [MVP++] Zytrm (Leader)
            ● [VIP] Fisher (Member)
            """.trimIndent(),
            "Zytrm",
        )

        val snapshot = PartyState.snapshot()
        assertTrue(snapshot.inParty)
        assertTrue(snapshot.listComplete)
        assertTrue(LootingVPartyCheck.canUseCachedParty(snapshot))
        assertEquals(setOf("Zytrm", "Fisher"), snapshot.members.toSet())
        assertTrue(PartyState.isPartyListResponse("Party Members (2)\n● [MVP++] Zytrm (Leader)"))
        PartyState.reset()
    }

    @Test
    fun knownJoinAndLeaveKeepCompleteCachedPartyState() {
        PartyState.reset()
        PartyState.applyMessage("You have created a party!", "LocalPlayer")
        assertTrue(PartyState.snapshot().listComplete)

        PartyState.applyMessage("Friend_One joined the party.", "LocalPlayer")
        assertTrue(PartyState.snapshot().listComplete)
        assertEquals(setOf("LocalPlayer", "Friend_One"), PartyState.snapshot().members.toSet())

        PartyState.applyMessage("Friend_One has left the party.", "LocalPlayer")
        assertTrue(PartyState.snapshot().listComplete)
        assertEquals(listOf("LocalPlayer"), PartyState.snapshot().members)
        PartyState.reset()
    }
}
