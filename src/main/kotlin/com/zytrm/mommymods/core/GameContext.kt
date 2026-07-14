package com.zytrm.mommymods.core

import net.minecraft.client.Minecraft

object GameContext {
    private val fishingAreas = setOf(
        "crimson isle",
        "jerry's workshop",
        "backwater bayou",
        "mushroom desert",
        "crystal hollows",
        "galatea",
        "lotus atoll",
        "spider's den",
        "the park",
    )
    private val areaLine = Regex("^(?:Area|Dungeon):\\s*(.+)$", RegexOption.IGNORE_CASE)

    fun isOnHypixel(): Boolean {
        val address = Minecraft.getInstance().currentServer?.ip ?: return false
        return address.substringBefore(':').lowercase().let {
            it == "hypixel.net" || it.endsWith(".hypixel.net")
        }
    }

    fun currentArea(): String? {
        val connection = Minecraft.getInstance().connection ?: return null
        return extractArea(connection.onlinePlayers.mapNotNull { it.tabListDisplayName?.string })
    }

    fun isFishingIsland(): Boolean = isFishingArea(currentArea())

    internal fun extractArea(lines: Iterable<String>): String? = lines.firstNotNullOfOrNull { line ->
        areaLine.matchEntire(line.trim())?.groupValues?.get(1)?.trim()
    }

    internal fun isFishingArea(area: String?): Boolean = area?.trim()?.lowercase() in fishingAreas
}
