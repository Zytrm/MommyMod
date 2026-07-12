package com.github.noamm9.mommymods.core

import com.github.noamm9.utils.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

object Chat {
    private fun prefix(): MutableComponent = Component.literal("[MommyMods] ")
        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)

    fun info(message: String) {
        ChatUtils.chat(prefix().append(Component.literal(message).withStyle(ChatFormatting.GRAY)))
    }

    fun component(component: Component) {
        ChatUtils.chat(prefix().append(component))
    }
}
