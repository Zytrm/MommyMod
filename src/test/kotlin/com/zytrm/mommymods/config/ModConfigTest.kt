package com.zytrm.mommymods.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModConfigTest {
    @Test
    fun acceptsOnlyCanonicalInstallationUuids() {
        assertTrue(isInstallationId("2f77bf75-a701-4af4-9704-bc776b229bbd"))
        assertFalse(isInstallationId("shared-client-secret"))
        assertFalse(isInstallationId("2f77bf75a7014af49704bc776b229bbd"))
    }
}
