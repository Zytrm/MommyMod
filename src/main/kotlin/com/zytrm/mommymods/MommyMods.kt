package com.zytrm.mommymods

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.core.Chat
import com.zytrm.mommymods.feature.FishingPartyHelper
import com.zytrm.mommymods.feature.JawbusFinder
import com.zytrm.mommymods.feature.LouderCatch
import com.zytrm.mommymods.feature.LootingVMessage
import com.zytrm.mommymods.feature.MediaPlayer
import com.zytrm.mommymods.feature.PartyState
import com.zytrm.mommymods.ui.MommyConfigScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory

object MommyMods : ClientModInitializer {
    const val MOD_ID = "mommymods"
    val logger = LoggerFactory.getLogger("MommyMods")
    @Volatile private var openMenuNextTick = false

    @JvmStatic
    fun requestMenuOpen() {
        openMenuNextTick = true
    }

    override fun onInitializeClient() {
        ModConfig.load()
        MediaPlayer.initialize()

        ClientReceiveMessageEvents.GAME.register { component, overlay ->
            if (!overlay) {
                val message = component.string
                PartyState.onMessage(message)
                FishingPartyHelper.onMessage(message)
                JawbusFinder.onMessage(message)
                LootingVMessage.onMessage(message)
            }
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val openScreen = {
                requestMenuOpen()
                1
            }
            dispatcher.register(ClientCommands.literal("mommymods").executes { openScreen() })
            dispatcher.register(ClientCommands.literal("mm").executes { openScreen() })
            dispatcher.register(
                ClientCommands.literal("mmplay")
                    .executes {
                        MediaPlayer.openScreen(Minecraft.getInstance().screen)
                        1
                    }
                    .then(
                        ClientCommands.argument("query", StringArgumentType.greedyString()).executes { context ->
                            MediaPlayer.play(StringArgumentType.getString(context, "query"))
                            1
                        },
                    ),
            )
            dispatcher.register(
                ClientCommands.literal("mmmedia")
                    .executes {
                        MediaPlayer.openScreen(Minecraft.getInstance().screen)
                        1
                    }
                    .then(ClientCommands.literal("open").executes {
                        MediaPlayer.openScreen(Minecraft.getInstance().screen)
                        1
                    })
                    .then(
                        ClientCommands.literal("play")
                            .then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes { context ->
                                MediaPlayer.play(StringArgumentType.getString(context, "query"))
                                1
                            }),
                    )
                    .then(ClientCommands.literal("pause").executes {
                        MediaPlayer.togglePause()
                        1
                    })
                    .then(ClientCommands.literal("next").executes {
                        MediaPlayer.next()
                        1
                    })
                    .then(ClientCommands.literal("previous").executes {
                        MediaPlayer.previous()
                        1
                    })
                    .then(ClientCommands.literal("stop").executes {
                        MediaPlayer.stop()
                        1
                    })
                    .then(ClientCommands.literal("shuffle").executes {
                        MediaPlayer.toggleShuffle()
                        1
                    })
                    .then(ClientCommands.literal("repeat").executes {
                        MediaPlayer.cycleRepeat()
                        1
                    })
                    .then(
                        ClientCommands.literal("seek")
                            .then(ClientCommands.argument("seconds", LongArgumentType.longArg(0L)).executes { context ->
                                MediaPlayer.seekTo(LongArgumentType.getLong(context, "seconds") * 1_000L)
                                1
                            }),
                    )
                    .then(ClientCommands.literal("signin").executes {
                        MediaPlayer.startYoutubeSignIn()
                        1
                    })
                    .then(
                        ClientCommands.literal("volume")
                            .then(ClientCommands.argument("percent", IntegerArgumentType.integer(0, 100)).executes { context ->
                                MediaPlayer.setVolume(IntegerArgumentType.getInteger(context, "percent") / 100f)
                                1
                            }),
                    ),
            )
            dispatcher.register(ClientCommands.literal("mmcatchdebug").executes {
                val enabled = LouderCatch.toggleDiagnostics()
                Chat.info("LouderCatch diagnostics ${if (enabled) "enabled" else "disabled"}.")
                1
            })
            dispatcher.register(
                ClientCommands.literal("mmpartydebug")
                    .executes {
                        Chat.info("Use /mmpartydebug self, profile <player>, status, or message.")
                        1
                    }
                    .then(ClientCommands.literal("self").executes {
                        FishingPartyHelper.debugSelf()
                        1
                    })
                    .then(
                        ClientCommands.literal("profile")
                            .then(
                                ClientCommands.argument("player", StringArgumentType.word())
                                    .executes { context ->
                                        FishingPartyHelper.debugInspect(StringArgumentType.getString(context, "player"))
                                        1
                                    },
                            ),
                    )
                    .then(ClientCommands.literal("status").executes {
                        FishingPartyHelper.debugStatus()
                        1
                    })
                    .then(ClientCommands.literal("message").executes {
                        LootingVMessage.debugPreview()
                        1
                    }),
            )
            dispatcher.register(
                ClientCommands.literal("mommy")
                    .then(ClientCommands.literal("mods").executes { openScreen() })
            )
        }

        ClientTickEvents.END_CLIENT_TICK.register { minecraft ->
            LouderCatch.onTick(minecraft)
            FishingPartyHelper.onTick(minecraft)
            LootingVMessage.onTick(minecraft)
            if (openMenuNextTick) {
                openMenuNextTick = false
                if (minecraft.screen !is MommyConfigScreen) {
                    minecraft.setScreen(MommyConfigScreen(minecraft.screen))
                }
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            PartyState.reset()
            JawbusFinder.reset()
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            MediaPlayer.shutdown()
            ModConfig.save()
        }
        logger.info("MommyMods initialized")
    }
}
