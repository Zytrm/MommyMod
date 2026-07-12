package com.github.noamm9.mommymods

import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

object MommyMods : ClientModInitializer {
    const val MOD_ID = "mommymods"
    @JvmField
    val logger = LoggerFactory.getLogger("MommyMods")

    override fun onInitializeClient() {
        logger.info("MommyMods initialized")
    }
}
