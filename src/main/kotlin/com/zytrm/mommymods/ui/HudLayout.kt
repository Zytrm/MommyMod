package com.zytrm.mommymods.ui

import com.zytrm.mommymods.config.ModConfig

enum class HudElement {
    JAWBUS,
    MEDIA,
}

object HudLayout {
    fun position(element: HudElement, width: Int, height: Int, guiWidth: Int, guiHeight: Int): Pair<Int, Int> {
        val (centerX, centerY) = configuredCenter(element)
        val default = when (element) {
            HudElement.JAWBUS -> (guiWidth - width) / 2 to 18
            HudElement.MEDIA -> guiWidth - width - 8 to guiHeight - height - 44
        }
        if (centerX < 0f || centerY < 0f) return default

        val x = (centerX * guiWidth - width / 2f).toInt().coerceIn(0, (guiWidth - width).coerceAtLeast(0))
        val y = (centerY * guiHeight - height / 2f).toInt().coerceIn(0, (guiHeight - height).coerceAtLeast(0))
        return x to y
    }

    fun setPosition(
        element: HudElement,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        guiWidth: Int,
        guiHeight: Int,
    ) {
        val centerX = ((x + width / 2f) / guiWidth.coerceAtLeast(1)).coerceIn(0f, 1f)
        val centerY = ((y + height / 2f) / guiHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
        when (element) {
            HudElement.JAWBUS -> {
                ModConfig.values.jawbusHudCenterX = centerX
                ModConfig.values.jawbusHudCenterY = centerY
            }
            HudElement.MEDIA -> {
                ModConfig.values.mediaHudCenterX = centerX
                ModConfig.values.mediaHudCenterY = centerY
            }
        }
    }

    fun reset() {
        ModConfig.values.jawbusHudCenterX = -1f
        ModConfig.values.jawbusHudCenterY = -1f
        ModConfig.values.mediaHudCenterX = -1f
        ModConfig.values.mediaHudCenterY = -1f
    }

    private fun configuredCenter(element: HudElement): Pair<Float, Float> = when (element) {
        HudElement.JAWBUS -> ModConfig.values.jawbusHudCenterX to ModConfig.values.jawbusHudCenterY
        HudElement.MEDIA -> ModConfig.values.mediaHudCenterX to ModConfig.values.mediaHudCenterY
    }
}
