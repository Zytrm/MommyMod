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

    @Test
    fun appliesNewFeatureDefaultsToAnExistingOlderConfig() {
        val values = MommySettings(
            partyReadinessHud = false,
            jawbusFinisherEnabled = false,
            jawbusFinisherHealth = 0,
            jawbusFinisherPartyMessage = false,
            jawbusFinisherMessage = "",
        )

        applyMissingSettingDefaults(values, setOf("hideFishingLine", "louderCatch"))

        assertTrue(values.partyReadinessHud)
        assertTrue(values.jawbusFinisherEnabled)
        assertTrue(values.jawbusFinisherPartyMessage)
        assertTrue(values.jawbusFinisherHealth == 20)
        assertTrue(values.jawbusFinisherMessage == DEFAULT_FINISHER_MESSAGE)
    }
}
