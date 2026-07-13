package com.zytrm.mommymods.feature

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

object LootingVScanner {
    private val lootingV = Regex("\\bLooting\\s+V\\b", RegexOption.IGNORE_CASE)

    fun localHotbar(minecraft: Minecraft): List<String>? {
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        return (0 until Inventory.getSelectionSize())
            .map(player.inventory::getItem)
            .mapNotNull { stack -> classify(itemText(minecraft, level, stack)) }
            .filter { it.second }
            .map { it.first }
            .distinct()
    }

    internal fun classify(text: String): Pair<String, Boolean>? {
        val weapon = when {
            text.contains("hyperion", ignoreCase = true) -> "Hyperion"
            text.contains("flaming flay", ignoreCase = true) -> "Flaming Flay"
            else -> return null
        }
        return weapon to lootingV.containsMatchIn(text)
    }

    private fun itemText(minecraft: Minecraft, level: ClientLevel, stack: ItemStack): String {
        if (stack.isEmpty) return ""
        return buildString {
            appendLine(stack.hoverName.string)
            runCatching {
                stack.getTooltipLines(Item.TooltipContext.of(level), minecraft.player, TooltipFlag.NORMAL)
            }.getOrDefault(emptyList()).forEach { appendLine(it.string) }
        }
    }
}
