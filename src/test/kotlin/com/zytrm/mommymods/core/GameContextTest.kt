package com.zytrm.mommymods.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameContextTest {
    @Test
    fun extractsAreaFromVisibleTabData() {
        assertEquals("Crimson Isle", GameContext.extractArea(listOf("Players (24)", "Area: Crimson Isle", "Profile: Peach")))
        assertEquals("Catacombs", GameContext.extractArea(listOf("Dungeon: Catacombs")))
    }

    @Test
    fun recognizesFishingIslandsOnly() {
        assertTrue(GameContext.isFishingArea("Crimson Isle"))
        assertTrue(GameContext.isFishingArea("Jerry's Workshop"))
        assertTrue(GameContext.isFishingArea("Backwater Bayou"))
        assertTrue(GameContext.isFishingArea("Crystal Hollows"))
        assertTrue(GameContext.isFishingArea("Lotus Atoll"))
        assertFalse(GameContext.isFishingArea("Dungeon Hub"))
        assertFalse(GameContext.isFishingArea(null))
    }
}
