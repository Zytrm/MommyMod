package com.zytrm.mommymods.feature

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RareDropScreenshotTest {
    @Test
    fun classifiesStrictRareAnnouncementsAndLocalRewards() {
        assertEquals(
            RareDropScreenshot.Trigger.DYE_OR_VIAL,
            RareDropScreenshot.classify("CRAZY RARE DROP! Radioactive Vial (+200% Magic Find)", "Zytrm"),
        )
        assertEquals(
            RareDropScreenshot.Trigger.DYE_OR_VIAL,
            RareDropScreenshot.classify("You found a Carmine Dye!", "Zytrm"),
        )
        assertEquals(
            RareDropScreenshot.Trigger.RARE_REWARD,
            RareDropScreenshot.classify("RARE REWARD! Sharpness VII", "Zytrm"),
        )
        assertEquals(
            RareDropScreenshot.Trigger.RARE_REWARD,
            RareDropScreenshot.classify("You uncovered Metaphysical Serum!", "Zytrm"),
        )
    }

    @Test
    fun ignoresOtherPlayersAndChatMessages() {
        assertNull(RareDropScreenshot.classify("WOW! OtherPlayer found a Mango Dye!", "Zytrm"))
        assertNull(RareDropScreenshot.classify("Party > Fisher: CRAZY RARE DROP!", "Zytrm"))
        assertNull(RareDropScreenshot.classify("Guild > Fisher: You found a Carmine Dye!", "Zytrm"))
    }
}
