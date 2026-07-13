package com.zytrm.mommymods.feature

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JawbusFinisherHelperTest {
    @Test
    fun parsesCompactJawbusHealthNames() {
        assertEquals(
            JawbusFinisherHelper.HealthSample(12_500_000.0, 100_000_000.0),
            JawbusFinisherHelper.parseHealth("[Lv600] Lord Jawbus 12.5M/100M\u2764"),
        )
        assertEquals(
            JawbusFinisherHelper.HealthSample(850_000.0, null),
            JawbusFinisherHelper.parseHealth("Lord Jawbus 850k\u2764"),
        )
        assertNull(JawbusFinisherHelper.parseHealth("Lord Jawbus"))
    }

    @Test
    fun formatsElapsedJawbusTimeCompactly() {
        assertEquals("0s", JawbusTimePartyCommand.formatElapsed(500L))
        assertEquals("2m 5s", JawbusTimePartyCommand.formatElapsed(125_000L))
        assertEquals("1h 2m 3s", JawbusTimePartyCommand.formatElapsed(3_723_000L))
        assertEquals("1d 1h 0m 0s", JawbusTimePartyCommand.formatElapsed(90_000_000L))
    }
}
