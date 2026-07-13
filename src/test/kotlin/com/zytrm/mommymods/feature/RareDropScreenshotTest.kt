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
        assertEquals(
            RareDropScreenshot.Trigger.RARE_REWARD,
            RareDropScreenshot.classify("[MVP++] Zytrm just found a Necron's Handle!", "Zytrm"),
        )
        assertEquals(
            RareDropScreenshot.Trigger.RNG_DROP,
            RareDropScreenshot.classify("RNG DROP! [MVP++] Zytrm just found a Necron's Handle!", "Zytrm"),
        )
        assertEquals(
            RareDropScreenshot.Trigger.DYE_OR_VIAL,
            RareDropScreenshot.classify("RARE DROP! Carmine Dye (+130 Magic Find)", "Zytrm"),
        )
    }

    @Test
    fun ignoresOtherPlayersAndChatMessages() {
        assertNull(RareDropScreenshot.classify("WOW! OtherPlayer found a Mango Dye!", "Zytrm"))
        assertNull(RareDropScreenshot.classify("CRAZY RARE DROP! [MVP++] OtherPlayer found a Mango Dye!", "Zytrm"))
        assertNull(RareDropScreenshot.classify("RNG DROP! [MVP++] OtherPlayer just found a Necron's Handle!", "Zytrm"))
        assertNull(RareDropScreenshot.classify("Party > Fisher: CRAZY RARE DROP!", "Zytrm"))
        assertNull(RareDropScreenshot.classify("Guild > Fisher: You found a Carmine Dye!", "Zytrm"))
        assertNull(RareDropScreenshot.classify("[MVP++] Fisher: RARE REWARD! Sharpness VII", "Zytrm"))
    }

    @Test
    fun ignoresOrdinaryDungeonAndReminderMessages() {
        assertNull(
            RareDropScreenshot.classify(
                "-----------------------------\n[MVP++] Unicked entered The Catacombs, Floor VII!\n-----------------------------",
                "Zytrm",
            ),
        )
        assertNull(RareDropScreenshot.classify("You have 4 pending Bestiary Milestones to be claimed!", "Zytrm"))
        assertNull(RareDropScreenshot.classify("You haven't claimed your Summer Rewards yet!", "Zytrm"))
        assertNull(RareDropScreenshot.classify("You sold Healing VIII Splash Potion x1 for 22,833 Coins!", "Zytrm"))
        assertNull(RareDropScreenshot.classify("RARE DROP! Earthen Blade (+130 Magic Find)", "Zytrm"))
    }
}
