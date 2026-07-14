package com.zytrm.mommymods.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class MommyConfigScreenTest {
    @Test
    fun scrollStepsArePredictableAndClamped() {
        assertEquals(4.1f, MommyConfigScreen.stepValue(4.0f, 1, 0.1f, 20f, 0.1f), 0.0001f)
        assertEquals(3.9f, MommyConfigScreen.stepValue(4.0f, -1, 0.1f, 20f, 0.1f), 0.0001f)
        assertEquals(20f, MommyConfigScreen.stepValue(20f, 1, 0.1f, 20f, 0.1f), 0.0001f)
        assertEquals(0f, MommyConfigScreen.stepValue(0f, -1, 0f, 1f, 0.05f), 0.0001f)
    }
}
