package com.zytrm.mommymods.feature

import com.zytrm.mommymods.model.FishingReadiness
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

object LootingVScanner {
    private val formattingCode = Regex("\u00a7.")
    private val lootingV = Regex("\\bLooting\\s*(?:V|5)\\b", RegexOption.IGNORE_CASE)

    data class Result(
        val validWeapons: List<String>,
        val lootingVWeapons: List<String>,
    ) {
        val hasLootingV: Boolean get() = lootingVWeapons.isNotEmpty()
    }

    fun localInventory(minecraft: Minecraft): Result? {
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        return scan(
            sequence {
                yieldAll(player.inventory.nonEquipmentItems)
                yield(player.offhandItem)
            }
                .mapNotNull { stack -> classifyStack(minecraft, level, stack) }
                .toList(),
        )
    }

    internal fun classify(text: String): Pair<String, Boolean>? {
        val cleanText = formattingCode.replace(text, "")
        val weapon = canonicalWeapon(cleanText) ?: return null
        return weapon to lootingV.containsMatchIn(cleanText)
    }

    fun readinessWeapons(readiness: FishingReadiness): List<String>? = when (readiness.hasLootingV) {
        null -> null
        false -> emptyList()
        true -> (readiness.lootingWeapons + listOfNotNull(readiness.lootingWeapon))
            .mapNotNull(::canonicalWeapon)
            .distinct()
            .ifEmpty { listOf("Weapon") }
    }

    internal fun scan(items: Iterable<Pair<String, Boolean>>): Result {
        val valid = linkedSetOf<String>()
        val enchanted = linkedSetOf<String>()
        items.forEach { (weapon, hasLootingV) ->
            valid += weapon
            if (hasLootingV) enchanted += weapon
        }
        return Result(valid.toList(), enchanted.toList())
    }

    fun classifyStack(minecraft: Minecraft, level: ClientLevel, stack: ItemStack): Pair<String, Boolean>? {
        if (stack.isEmpty) return null
        return classify(itemText(minecraft, level, stack))
    }

    private fun canonicalWeapon(text: String): String? = when {
        text.contains("hyperion", ignoreCase = true) -> "Hyperion"
        text.contains("flaming flay", ignoreCase = true) -> "Flaming Flay"
        text.equals("weapon", ignoreCase = true) -> "Weapon"
        else -> null
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
