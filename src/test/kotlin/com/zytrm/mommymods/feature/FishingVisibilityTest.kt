package com.zytrm.mommymods.feature

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FishingVisibilityTest {
    @Test
    fun appliesEachBobberModeWithoutHidingTheInitialLocalCast() {
        assertFalse(BobberVisibility.shouldHideBobber(BobberVisibilityMode.LINE_ONLY, localOwner = false, submerged = true))
        assertTrue(BobberVisibility.shouldHideBobber(BobberVisibilityMode.HIDE_OTHERS, localOwner = false, submerged = false))
        assertFalse(BobberVisibility.shouldHideBobber(BobberVisibilityMode.HIDE_OTHERS, localOwner = true, submerged = true))
        assertTrue(BobberVisibility.shouldHideBobber(BobberVisibilityMode.HIDE_ALL, localOwner = false, submerged = false))
        assertFalse(BobberVisibility.shouldHideBobber(BobberVisibilityMode.HIDE_ALL, localOwner = true, submerged = false))
        assertTrue(BobberVisibility.shouldHideBobber(BobberVisibilityMode.HIDE_ALL, localOwner = true, submerged = true))
    }
}
