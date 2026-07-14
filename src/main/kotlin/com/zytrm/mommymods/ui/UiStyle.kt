package com.zytrm.mommymods.ui

import com.zytrm.mommymods.config.ModConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

object UiStyle {
    private data class Accent(val name: String, val color: Int)

    private val accents = listOf(
        Accent("Mommy Pink", 0xFFFF4F91.toInt()),
        Accent("Rose", 0xFFFF6B9D.toInt()),
        Accent("Violet", 0xFFB56CFF.toInt()),
        Accent("Cyan", 0xFF4FD8E8.toInt()),
        Accent("Mint", 0xFF55D6A0.toInt()),
        Accent("Gold", 0xFFFFB84F.toInt()),
    )

    val sortingOptions = listOf("A-Z Sorting", "Width Sorting", "No Sorting")

    val accent: Int
        get() = ModConfig.values.clickGuiAccent

    val accentBright: Int
        get() = adjust(accent, 52)

    val accentMuted: Int
        get() = adjust(accent, -82)

    val rowEnabled: Int
        get() = withAlpha(adjust(accent, -110), 0xD1)

    val accentName: String
        get() = accents.firstOrNull { it.color == accent }?.name ?: "Custom"

    fun cycleAccent(direction: Int = 1) {
        val index = accents.indexOfFirst { it.color == accent }.coerceAtLeast(0)
        ModConfig.values.clickGuiAccent = accents[(index + direction).mod(accents.size)].color
    }

    fun cycleSorting(direction: Int = 1) {
        val current = sortingOptions.indexOf(ModConfig.values.clickGuiSorting).coerceAtLeast(0)
        ModConfig.values.clickGuiSorting = sortingOptions[(current + direction).mod(sortingOptions.size)]
    }

    fun reset() {
        ModConfig.values.clickGuiSound = true
        ModConfig.values.clickGuiAccent = accents.first().color
        ModConfig.values.clickGuiSorting = "No Sorting"
        HudLayout.reset()
        ModConfig.save()
    }

    fun playClick(pitch: Float = 1.0f) {
        if (!ModConfig.values.clickGuiSound) return
        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance(
                SoundEvents.UI_BUTTON_CLICK.value().location,
                SoundSource.MASTER,
                0.65f,
                pitch,
                SoundInstance.createUnseededRandom(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0,
                0.0,
                0.0,
                true,
            ),
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    private fun adjust(color: Int, amount: Int): Int {
        val alpha = color ushr 24 and 0xFF
        val red = ((color ushr 16 and 0xFF) + amount).coerceIn(0, 255)
        val green = ((color ushr 8 and 0xFF) + amount).coerceIn(0, 255)
        val blue = ((color and 0xFF) + amount).coerceIn(0, 255)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
