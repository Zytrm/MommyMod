package com.github.noamm9.mommymods.core

import com.github.noamm9.utils.location.LocationUtils
import net.minecraft.client.Minecraft

object GameContext {
    fun isOnHypixel(): Boolean {
        if (LocationUtils.onHypixel) return true
        val address = Minecraft.getInstance().currentServer?.ip ?: return false
        return address.substringBefore(':').lowercase().let {
            it == "hypixel.net" || it.endsWith(".hypixel.net")
        }
    }
}
