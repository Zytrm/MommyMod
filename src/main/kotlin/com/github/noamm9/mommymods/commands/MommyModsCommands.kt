package com.github.noamm9.mommymods.commands

import com.github.noamm9.NoammAddons
import com.github.noamm9.commands.BaseCommand
import com.github.noamm9.commands.CommandNodeBuilder
import com.github.noamm9.mommymods.core.Chat
import com.github.noamm9.mommymods.features.impl.fishing.FishingPartyHelper
import com.github.noamm9.mommymods.features.impl.fishing.LootingVMessage
import com.github.noamm9.mommymods.features.impl.fishing.LouderCatch
import com.github.noamm9.ui.clickgui.ClickGuiScreen
import com.mojang.brigadier.arguments.StringArgumentType

private fun openMommyMods() {
    NoammAddons.screen = ClickGuiScreen
}

object MommyModsCommand : BaseCommand("mm", mutableSetOf("mommymods")) {
    override fun CommandNodeBuilder.build() {
        runs { openMommyMods() }
    }
}

object MommyAliasCommand : BaseCommand("mommy") {
    override fun CommandNodeBuilder.build() {
        literal("mods") { runs { openMommyMods() } }
        runs { Chat.info("Use /mommy mods to open the settings.") }
    }
}

object CatchDebugCommand : BaseCommand("mmcatchdebug") {
    override fun CommandNodeBuilder.build() {
        runs {
            val enabled = LouderCatch.toggleDiagnostics()
            Chat.info("LouderCatch diagnostics ${if (enabled) "enabled" else "disabled"}.")
        }
    }
}

object PartyDebugCommand : BaseCommand("mmpartydebug") {
    override fun CommandNodeBuilder.build() {
        literal("self") { runs { FishingPartyHelper.debugSelf() } }
        literal("profile") {
            argument("player", StringArgumentType.word()) {
                runs { context ->
                    FishingPartyHelper.debugInspect(StringArgumentType.getString(context, "player"))
                }
            }
        }
        literal("status") { runs { FishingPartyHelper.debugStatus() } }
        literal("message") { runs { LootingVMessage.debugPreview() } }
        runs { Chat.info("Use /mmpartydebug self, profile <player>, status, or message.") }
    }
}
