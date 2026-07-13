package com.zytrm.mommymods.feature

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JawbusFinderTest {
    @AfterTest
    fun cleanPartyState() {
        PartyState.reset()
    }

    @Test
    fun `matches only the Lord Jawbus death line`() {
        assertEquals("OutsidePlayer", JawbusFinder.extractVictim("☠ OutsidePlayer was killed by Lord Jawbus."))
        assertEquals("RankedPlayer", JawbusFinder.extractVictim(" ☠ [MVP+] RankedPlayer was killed by Lord Jawbus."))
        assertNull(JawbusFinder.extractVictim("OutsidePlayer was killed by Lord Jawbus."))
        assertNull(JawbusFinder.extractVictim("☠ OutsidePlayer was killed by Jawbus Follower."))
        assertNull(JawbusFinder.extractVictim("☠ OutsidePlayer was killed by Thunder."))
    }

    @Test
    fun `rejects local and party victims`() {
        PartyState.applyMessage("Party Members (2)", "LocalPlayer")
        PartyState.applyMessage("Party Leader: [MVP+] LocalPlayer ●", "LocalPlayer")
        PartyState.applyMessage("Party Members: [VIP] Friend_One ●", "LocalPlayer")

        assertNull(JawbusFinder.classify("☠ LocalPlayer was killed by Lord Jawbus.", "LocalPlayer", PartyState::isMember))
        assertNull(JawbusFinder.classify("☠ Friend_One was killed by Lord Jawbus.", "LocalPlayer", PartyState::isMember))
        assertEquals(
            "OutsidePlayer",
            JawbusFinder.classify("☠ OutsidePlayer was killed by Lord Jawbus.", "LocalPlayer", PartyState::isMember),
        )
    }

    @Test
    fun `party lifecycle keeps membership current`() {
        PartyState.applyMessage("You have joined PartyOwner's party!", "LocalPlayer")
        PartyState.applyMessage("[MVP+] Friend_One joined the party.", "LocalPlayer")
        assertTrue(PartyState.isMember("PartyOwner"))
        assertTrue(PartyState.isMember("Friend_One"))

        PartyState.applyMessage("Friend_One has left the party.", "LocalPlayer")
        assertFalse(PartyState.isMember("Friend_One"))

        PartyState.applyMessage("You left the party.", "LocalPlayer")
        assertFalse(PartyState.isMember("PartyOwner"))
    }
}
