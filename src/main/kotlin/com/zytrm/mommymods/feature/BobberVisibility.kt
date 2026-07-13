package com.zytrm.mommymods.feature

import com.zytrm.mommymods.config.ModConfig

enum class BobberVisibilityMode(val label: String) {
    LINE_ONLY("Line Only"),
    HIDE_OTHERS("Hide Others"),
    HIDE_ALL("Hide All"),
}

object BobberVisibility {
    val modes = BobberVisibilityMode.entries

    fun current(): BobberVisibilityMode = modes.firstOrNull {
        it.label == ModConfig.values.bobberVisibility
    } ?: BobberVisibilityMode.LINE_ONLY

    fun cycle() {
        val current = current()
        ModConfig.values.bobberVisibility = modes[(modes.indexOf(current) + 1) % modes.size].label
    }

    @JvmStatic
    fun shouldHideBobber(localOwner: Boolean, submerged: Boolean): Boolean {
        if (!ModConfig.values.hideFishingLine) return false
        return shouldHideBobber(current(), localOwner, submerged)
    }

    internal fun shouldHideBobber(
        mode: BobberVisibilityMode,
        localOwner: Boolean,
        submerged: Boolean,
    ): Boolean = when (mode) {
        BobberVisibilityMode.LINE_ONLY -> false
        BobberVisibilityMode.HIDE_OTHERS -> !localOwner
        BobberVisibilityMode.HIDE_ALL -> !localOwner || submerged
    }
}
