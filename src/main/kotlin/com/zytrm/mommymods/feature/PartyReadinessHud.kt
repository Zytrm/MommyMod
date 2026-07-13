package com.zytrm.mommymods.feature

import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.core.GameContext
import com.zytrm.mommymods.ui.UiStyle
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.scores.Objective

object PartyReadinessHud {
    private const val MAX_ROWS = 12
    private var sidebarRenderedThisFrame = false

    @JvmStatic
    fun beginFrame() {
        sidebarRenderedThisFrame = false
    }

    @JvmStatic
    fun renderSidebar(graphics: GuiGraphicsExtractor, objective: Objective) {
        val rows = rows() ?: return
        sidebarRenderedThisFrame = true
        val scoreRows = objective.scoreboard.listPlayerScores(objective)
            .asSequence()
            .filterNot { it.isHidden }
            .take(15)
            .count()
        val scoreboardBottom = graphics.guiHeight() / 2 + (scoreRows * 9) / 3
        val hudHeight = rows.size * 9 + 4
        val below = scoreboardBottom + 4
        val preferredY = if (below + hudHeight <= graphics.guiHeight() - 38) {
            below
        } else {
            graphics.guiHeight() / 2 - (scoreRows * 9) * 2 / 3 - hudHeight - 12
        }
        draw(graphics, rows, preferredY)
    }

    @JvmStatic
    fun renderFallback(graphics: GuiGraphicsExtractor) {
        if (sidebarRenderedThisFrame) return
        val rows = rows() ?: return
        draw(graphics, rows, 36)
    }

    private fun rows(): List<Component>? {
        if (!ModConfig.values.partyReadinessHud || !GameContext.isOnHypixel()) return null
        val statuses = FishingPartyHelper.hudStatuses().take(MAX_ROWS)
        if (statuses.isEmpty()) return null
        return buildList {
            add(
                Component.literal("MM PARTY ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                    .append(Component.literal("J L5 BS").withStyle(ChatFormatting.GRAY)),
            )
            statuses.forEach { status ->
                add(
                    Component.literal(status.name.take(16)).withStyle(ChatFormatting.WHITE)
                        .append(Component.literal("  J").withStyle(ChatFormatting.DARK_GRAY))
                        .append(state(status.canJawbus))
                        .append(Component.literal(" L5").withStyle(ChatFormatting.DARK_GRAY))
                        .append(state(status.lootingV))
                        .append(Component.literal(" BS").withStyle(ChatFormatting.DARK_GRAY))
                        .append(state(status.bloodshot)),
                )
            }
        }
    }

    private fun state(value: Boolean?): MutableComponent = Component.literal(
        when (value) {
            true -> "+"
            false -> "-"
            null -> "?"
        },
    ).withStyle(
        when (value) {
            true -> ChatFormatting.GREEN
            false -> ChatFormatting.RED
            null -> ChatFormatting.YELLOW
        },
    )

    private fun draw(graphics: GuiGraphicsExtractor, rows: List<Component>, preferredY: Int) {
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val width = rows.maxOf(font::width) + 8
        val height = rows.size * 9 + 4
        val x = graphics.guiWidth() - width - 3
        val y = preferredY.coerceIn(3, (graphics.guiHeight() - height - 38).coerceAtLeast(3))
        graphics.fill(x, y, x + width, y + height, 0xB0100C12.toInt())
        graphics.fill(x, y, x + width, y + 2, UiStyle.accent)
        rows.forEachIndexed { index, row ->
            graphics.text(font, row, x + 4, y + 3 + index * 9, -1, false)
        }
    }
}
