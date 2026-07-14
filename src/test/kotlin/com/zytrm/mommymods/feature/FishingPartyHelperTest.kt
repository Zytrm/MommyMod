package com.zytrm.mommymods.feature

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FishingPartyHelperTest {
    @Test
    fun checksOnlyRemoteJoinsOnFishingIslands() {
        assertTrue(FishingPartyHelper.shouldInspectJoin("Fisher", "Zytrm", true, true, true))
        assertFalse(FishingPartyHelper.shouldInspectJoin("Zytrm", "Zytrm", true, true, true))
        assertFalse(FishingPartyHelper.shouldInspectJoin("Fisher", "Zytrm", false, true, true))
        assertFalse(FishingPartyHelper.shouldInspectJoin("Fisher", "Zytrm", true, false, true))
        assertFalse(FishingPartyHelper.shouldInspectJoin("Fisher", "Zytrm", true, true, false))
    }
}
