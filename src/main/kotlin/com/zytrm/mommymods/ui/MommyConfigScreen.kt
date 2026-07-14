package com.zytrm.mommymods.ui

import com.zytrm.mommymods.config.ModConfig
import com.zytrm.mommymods.feature.FishingPartyHelper
import com.zytrm.mommymods.feature.JawbusFinisherHelper
import com.zytrm.mommymods.feature.LouderCatch
import com.zytrm.mommymods.feature.MediaPlayer
import com.zytrm.mommymods.feature.PartyCommands
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.round
import kotlin.math.roundToInt

class MommyConfigScreen(private val parent: Screen?) : Screen(Component.literal("MommyMods")) {
    private enum class FeatureEntry(
        val title: String,
        val hasOptions: Boolean,
    ) {
        HIDE_LINE("Hide Fishing Line", true),
        LOUDER_CATCH("LouderCatch", true),
        PARTY_HELPER("FishingPartyHelper", true),
        MM_PARTY("MM Party", true),
        JAWBUS_FINISHER("Jawbus Finisher", true),
        JAWBUS_FINDER("Jawbus Finder", true),
        MEDIA_PLAYER("Aura Player", true),
        PARTY_COMMANDS("Party Commands", true),
        CLICK_GUI("ClickGUI", true);

        fun enabled(): Boolean = when (this) {
            HIDE_LINE -> ModConfig.values.hideFishingLine
            LOUDER_CATCH -> ModConfig.values.louderCatch
            PARTY_HELPER -> ModConfig.values.fishingPartyHelper
            MM_PARTY -> ModConfig.values.fishingPartyHelper && ModConfig.values.partyReadinessHud
            JAWBUS_FINISHER -> ModConfig.values.jawbusFinisherEnabled
            JAWBUS_FINDER -> ModConfig.values.jawbusFinder
            MEDIA_PLAYER -> ModConfig.values.mediaPlayer
            PARTY_COMMANDS -> ModConfig.values.partyCommandsEnabled
            CLICK_GUI -> true
        }

        fun setEnabled(value: Boolean) {
            when (this) {
                HIDE_LINE -> ModConfig.values.hideFishingLine = value
                LOUDER_CATCH -> ModConfig.values.louderCatch = value
                PARTY_HELPER -> ModConfig.values.fishingPartyHelper = value
                MM_PARTY -> {
                    ModConfig.values.partyReadinessHud = value
                    if (value) ModConfig.values.fishingPartyHelper = true
                }
                JAWBUS_FINISHER -> ModConfig.values.jawbusFinisherEnabled = value
                JAWBUS_FINDER -> ModConfig.values.jawbusFinder = value
                MEDIA_PLAYER -> {
                    ModConfig.values.mediaPlayer = value
                    MediaPlayer.onEnabledChanged(value)
                }
                PARTY_COMMANDS -> ModConfig.values.partyCommandsEnabled = value
                CLICK_GUI -> Unit
            }
        }
    }

    private data class FeatureCategory(val title: String, val features: List<FeatureEntry>)
    private data class PanelLayout(val category: FeatureCategory, val x: Int, val y: Int, val width: Int)
    private enum class DraggingSlider { VOLUME, PITCH, MEDIA_VOLUME }

    private val categories = listOf(
        FeatureCategory(
            "FISHING",
            listOf(
                FeatureEntry.HIDE_LINE,
                FeatureEntry.LOUDER_CATCH,
                FeatureEntry.PARTY_HELPER,
                FeatureEntry.MM_PARTY,
                FeatureEntry.JAWBUS_FINISHER,
                FeatureEntry.JAWBUS_FINDER,
            ),
        ),
        FeatureCategory("MISC", listOf(FeatureEntry.MEDIA_PLAYER, FeatureEntry.PARTY_COMMANDS)),
        FeatureCategory("DEV", listOf(FeatureEntry.CLICK_GUI)),
    )

    private var openFeature: FeatureEntry? = null
    private var windowX = 0
    private var windowY = 0
    private var draggingWindow = false
    private var windowDragX = 0
    private var windowDragY = 0
    private var draggingSlider: DraggingSlider? = null
    private lateinit var finisherMessageBox: EditBox
    private val partyAliasBoxes = mutableMapOf<String, EditBox>()

    private val accent: Int get() = UiStyle.accent
    private val accentBright: Int get() = UiStyle.accentBright
    private val accentMuted: Int get() = UiStyle.accentMuted
    private val panelBackground = 0xD9100C12.toInt()
    private val rowBackground = 0xC70A080B.toInt()
    private val rowHover = 0xE12A1722.toInt()
    private val rowEnabled: Int get() = UiStyle.rowEnabled
    private val windowBackground = 0xF20B080D.toInt()
    private val windowInner = 0xC9161019.toInt()
    private val textColor = 0xFFFFE8F0.toInt()
    private val mutedText = 0xFFCDB2BE.toInt()
    private val disabledText = 0xFF786A71.toInt()

    override fun init() {
        super.init()
        finisherMessageBox = EditBox(font, 0, 0, 200, 16, Component.literal("Jawbus finisher message"))
        finisherMessageBox.setMaxLength(256)
        finisherMessageBox.setValue(ModConfig.values.jawbusFinisherMessage)
        finisherMessageBox.setResponder { value -> ModConfig.values.jawbusFinisherMessage = value }
        finisherMessageBox.visible = false
        addRenderableWidget(finisherMessageBox)

        PartyCommands.definitions.forEach { definition ->
            val box = EditBox(font, 0, 0, 92, 16, Component.literal("${definition.label} alias"))
            box.setMaxLength(24)
            box.setValue(PartyCommands.alias(definition))
            box.setResponder { value ->
                if (PartyCommands.acceptsAliasInput(value)) PartyCommands.setting(definition).alias = value.lowercase()
            }
            box.visible = false
            partyAliasBoxes[definition.id] = box
            addRenderableWidget(box)
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawPanels(graphics, mouseX, mouseY)

        val selected = openFeature
        finisherMessageBox.visible = selected == FeatureEntry.JAWBUS_FINISHER
        partyAliasBoxes.values.forEach { it.visible = selected == FeatureEntry.PARTY_COMMANDS }
        if (selected != null) {
            graphics.fill(0, 0, width, height, 0x76000000)
            drawConfigWindow(graphics, selected, mouseX, mouseY)
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    private fun drawPanels(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        panelLayouts().forEach { panel ->
            val headerHeight = 22
            val featureHeight = 16
            val features = orderedFeatures(panel.category)
            val totalHeight = headerHeight + features.size * featureHeight

            graphics.fill(panel.x, panel.y, panel.x + panel.width, panel.y + totalHeight, panelBackground)
            graphics.fill(panel.x, panel.y, panel.x + panel.width, panel.y + 2, accent)
            graphics.centeredText(font, panel.category.title, panel.x + panel.width / 2, panel.y + 7, textColor)

            features.forEachIndexed { index, feature ->
                val rowY = panel.y + headerHeight + index * featureHeight
                val hovered = mouseX in panel.x until (panel.x + panel.width) && mouseY in rowY until (rowY + featureHeight)
                val enabled = feature.enabled()
                val color = when {
                    hovered -> rowHover
                    enabled -> rowEnabled
                    else -> rowBackground
                }
                graphics.fill(panel.x, rowY, panel.x + panel.width, rowY + featureHeight - 1, color)
                graphics.fill(panel.x, rowY, panel.x + 2, rowY + featureHeight, if (enabled) accentBright else accentMuted)
                graphics.centeredText(font, feature.title, panel.x + panel.width / 2, rowY + 4, if (enabled) textColor else mutedText)
            }
        }
    }

    private fun drawConfigWindow(graphics: GuiGraphicsExtractor, feature: FeatureEntry, mouseX: Int, mouseY: Int) {
        val (windowWidth, windowHeight) = windowSize(feature)
        windowX = windowX.coerceIn(4, (width - windowWidth - 4).coerceAtLeast(4))
        windowY = windowY.coerceIn(4, (height - windowHeight - 4).coerceAtLeast(4))

        graphics.fill(windowX - 2, windowY - 2, windowX + windowWidth + 2, windowY + windowHeight + 2, 0xE0000000.toInt())
        graphics.fill(windowX, windowY, windowX + windowWidth, windowY + windowHeight, windowBackground)
        graphics.fill(windowX, windowY, windowX + windowWidth, windowY + 2, accent)
        graphics.fill(windowX + 2, windowY + 2, windowX + windowWidth - 2, windowY + 24, 0xF01A151C.toInt())
        graphics.centeredText(font, feature.title, windowX + windowWidth / 2, windowY + 8, textColor)

        val closeHovered = mouseX in (windowX + windowWidth - 21) until (windowX + windowWidth - 5) &&
            mouseY in (windowY + 4) until (windowY + 20)
        graphics.fill(
            windowX + windowWidth - 21,
            windowY + 4,
            windowX + windowWidth - 5,
            windowY + 20,
            if (closeHovered) accentMuted else 0xFF292329.toInt(),
        )
        graphics.centeredText(font, "x", windowX + windowWidth - 13, windowY + 7, textColor)

        when (feature) {
            FeatureEntry.HIDE_LINE -> drawHideLineWindow(graphics, windowWidth, mouseX, mouseY)
            FeatureEntry.LOUDER_CATCH -> drawLouderCatchWindow(graphics, windowWidth, mouseX, mouseY)
            FeatureEntry.PARTY_HELPER -> drawPartyHelperWindow(graphics, windowWidth, mouseX, mouseY)
            FeatureEntry.MM_PARTY -> drawMmPartyWindow(graphics, windowWidth, mouseX, mouseY)
            FeatureEntry.JAWBUS_FINISHER -> drawJawbusFinisherWindow(graphics, windowWidth, mouseX, mouseY)
            FeatureEntry.JAWBUS_FINDER -> drawJawbusWindow(graphics, windowWidth, mouseX, mouseY)
            FeatureEntry.MEDIA_PLAYER -> drawMediaPlayerWindow(graphics, windowWidth, mouseX, mouseY)
            FeatureEntry.PARTY_COMMANDS -> drawPartyCommandsWindow(graphics, windowWidth, mouseX, mouseY)
            FeatureEntry.CLICK_GUI -> drawClickGuiWindow(graphics, windowWidth, mouseX, mouseY)
        }
    }

    private fun drawHideLineWindow(graphics: GuiGraphicsExtractor, windowWidth: Int, mouseX: Int, mouseY: Int) {
        drawToggleSetting(graphics, windowY + 30, windowWidth, "Enabled", ModConfig.values.hideFishingLine, true, mouseX, mouseY)
    }

    private fun drawLouderCatchWindow(graphics: GuiGraphicsExtractor, windowWidth: Int, mouseX: Int, mouseY: Int) {
        drawToggleSetting(graphics, windowY + 30, windowWidth, "Enabled", ModConfig.values.louderCatch, true, mouseX, mouseY)
        drawValueSetting(graphics, windowY + 52, windowWidth, "Sound", ModConfig.values.catchSound, mouseX, mouseY)
        drawSliderSetting(graphics, windowY + 74, windowWidth, "Volume", ModConfig.values.catchVolume, 0.1f, 20f, "${"%.1f".format(ModConfig.values.catchVolume)}x")
        drawSliderSetting(graphics, windowY + 96, windowWidth, "Pitch", ModConfig.values.catchPitch, 0.5f, 2f, "${"%.2f".format(ModConfig.values.catchPitch)}x")
        drawActionSetting(graphics, windowY + 118, windowWidth, "Test Sound", "PLAY", mouseX, mouseY)
    }

    private fun drawPartyHelperWindow(graphics: GuiGraphicsExtractor, windowWidth: Int, mouseX: Int, mouseY: Int) {
        drawToggleSetting(graphics, windowY + 30, windowWidth, "Enabled", ModConfig.values.fishingPartyHelper, true, mouseX, mouseY)
        drawToggleSetting(graphics, windowY + 52, windowWidth, "Auto Kick", ModConfig.values.autoKick, true, mouseX, mouseY)
        drawToggleSetting(graphics, windowY + 74, windowWidth, "No Looting V", ModConfig.values.kickNoLootingV, ModConfig.values.autoKick, mouseX, mouseY)
        drawToggleSetting(graphics, windowY + 96, windowWidth, "Can't Jawbus", ModConfig.values.kickCantJawbus, ModConfig.values.autoKick, mouseX, mouseY)
    }

    private fun drawMmPartyWindow(graphics: GuiGraphicsExtractor, windowWidth: Int, mouseX: Int, mouseY: Int) {
        drawToggleSetting(graphics, windowY + 30, windowWidth, "Enabled", FeatureEntry.MM_PARTY.enabled(), true, mouseX, mouseY)
        drawActionSetting(graphics, windowY + 52, windowWidth, "Readiness", "REFRESH", mouseX, mouseY)
    }

    private fun drawJawbusFinisherWindow(graphics: GuiGraphicsExtractor, windowWidth: Int, mouseX: Int, mouseY: Int) {
        drawToggleSetting(graphics, windowY + 30, windowWidth, "Enabled", ModConfig.values.jawbusFinisherEnabled, true, mouseX, mouseY)
        drawValueSetting(graphics, windowY + 52, windowWidth, "Trigger Health", "${ModConfig.values.jawbusFinisherHealth}%", mouseX, mouseY)
        drawToggleSetting(graphics, windowY + 74, windowWidth, "Party Callout", ModConfig.values.jawbusFinisherPartyMessage, ModConfig.values.jawbusFinisherEnabled, mouseX, mouseY)
        graphics.fill(windowX + 10, windowY + 96, windowX + windowWidth - 10, windowY + 116, windowInner)
        graphics.text(font, "Message", windowX + 16, windowY + 102, textColor, false)
        finisherMessageBox.setX(windowX + 68)
        finisherMessageBox.setY(windowY + 98)
        finisherMessageBox.setWidth(windowWidth - 84)
        drawActionSetting(graphics, windowY + 118, windowWidth, "Finisher Alert", "PREVIEW", mouseX, mouseY)
    }

    private fun drawJawbusWindow(graphics: GuiGraphicsExtractor, windowWidth: Int, mouseX: Int, mouseY: Int) {
        drawToggleSetting(graphics, windowY + 30, windowWidth, "Enabled", ModConfig.values.jawbusFinder, true, mouseX, mouseY)
        drawToggleSetting(
            graphics,
            windowY + 52,
            windowWidth,
            "Death Message Detection",
            ModConfig.values.deathMessageDetection,
            ModConfig.values.jawbusFinder,
            mouseX,
            mouseY,
        )
    }

    private fun drawMediaPlayerWindow(graphics: GuiGraphicsExtractor, windowWidth: Int, mouseX: Int, mouseY: Int) {
        val enabled = ModConfig.values.mediaPlayer
        drawToggleSetting(graphics, windowY + 30, windowWidth, "Enabled", enabled, true, mouseX, mouseY)
        drawToggleSetting(graphics, windowY + 52, windowWidth, "Now Playing HUD", ModConfig.values.mediaHud, enabled, mouseX, mouseY)
        drawToggleSetting(graphics, windowY + 74, windowWidth, "Autoplay Playlists", ModConfig.values.mediaAutoplay, enabled, mouseX, mouseY)
        drawSliderSetting(
            graphics,
            windowY + 96,
            windowWidth,
            "Volume",
            ModConfig.values.mediaVolume,
            0f,
            1f,
            "${(ModConfig.values.mediaVolume * 100).roundToInt()}%",
        )
        val playback = when {
            MediaPlayer.currentInfo() == null -> "OPEN"
            MediaPlayer.isPaused() -> "RESUME"
            else -> "PAUSE"
        }
        drawActionSetting(graphics, windowY + 118, windowWidth, "Now Playing", playback, mouseX, mouseY)
        drawActionSetting(graphics, windowY + 140, windowWidth, "Full Player", "OPEN", mouseX, mouseY)
    }

    private fun drawPartyCommandsWindow(graphics: GuiGraphicsExtractor, windowWidth: Int, mouseX: Int, mouseY: Int) {
        val featureEnabled = ModConfig.values.partyCommandsEnabled
        drawToggleSetting(graphics, windowY + 30, windowWidth, "Enabled", featureEnabled, true, mouseX, mouseY)
        PartyCommands.definitions.forEachIndexed { index, definition ->
            val setting = PartyCommands.setting(definition)
            val toggleY = windowY + 52 + index * 44
            val aliasY = toggleY + 22
            drawToggleSetting(graphics, toggleY, windowWidth, definition.label, setting.enabled, featureEnabled, mouseX, mouseY)
            graphics.fill(windowX + 10, aliasY, windowX + windowWidth - 10, aliasY + 20, windowInner)
            graphics.text(font, "Alias", windowX + 16, aliasY + 6, if (featureEnabled && setting.enabled) textColor else disabledText, false)
            val box = partyAliasBoxes.getValue(definition.id)
            box.setX(windowX + windowWidth - 112)
            box.setY(aliasY + 2)
            box.setWidth(92)
            box.setEditable(featureEnabled && setting.enabled)
            box.setTextColor(if (featureEnabled && setting.enabled) accentBright else disabledText)
        }
    }

    private fun drawClickGuiWindow(graphics: GuiGraphicsExtractor, windowWidth: Int, mouseX: Int, mouseY: Int) {
        drawToggleSetting(graphics, windowY + 30, windowWidth, "Click Sound", ModConfig.values.clickGuiSound, true, mouseX, mouseY)
        drawValueSetting(graphics, windowY + 52, windowWidth, "Accent Color", UiStyle.accentName, mouseX, mouseY)
        drawValueSetting(graphics, windowY + 74, windowWidth, "Sorting", ModConfig.values.clickGuiSorting, mouseX, mouseY)
        drawActionSetting(graphics, windowY + 96, windowWidth, "HUD Editor", "OPEN", mouseX, mouseY)
        drawActionSetting(graphics, windowY + 118, windowWidth, "Settings", "RESET", mouseX, mouseY)
    }

    private fun drawToggleSetting(
        graphics: GuiGraphicsExtractor,
        y: Int,
        windowWidth: Int,
        label: String,
        value: Boolean,
        active: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = active && mouseX in (windowX + 10) until (windowX + windowWidth - 10) && mouseY in y until (y + 20)
        graphics.fill(windowX + 10, y, windowX + windowWidth - 10, y + 20, if (hovered) rowHover else windowInner)
        graphics.text(font, label, windowX + 16, y + 6, if (active) textColor else disabledText, false)
        val switchX = windowX + windowWidth - 45
        graphics.fill(switchX, y + 5, switchX + 24, y + 15, if (value && active) accentMuted else 0xFF3D343A.toInt())
        val knobX = if (value && active) switchX + 14 else switchX + 2
        graphics.fill(knobX, y + 6, knobX + 8, y + 14, if (active) textColor else disabledText)
    }

    private fun drawValueSetting(
        graphics: GuiGraphicsExtractor,
        y: Int,
        windowWidth: Int,
        label: String,
        value: String,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = mouseX in (windowX + 10) until (windowX + windowWidth - 10) && mouseY in y until (y + 20)
        graphics.fill(windowX + 10, y, windowX + windowWidth - 10, y + 20, if (hovered) rowHover else windowInner)
        graphics.text(font, label, windowX + 16, y + 6, textColor, false)
        graphics.text(font, value, windowX + windowWidth - 16 - font.width(value), y + 6, accentBright, false)
    }

    private fun drawSliderSetting(
        graphics: GuiGraphicsExtractor,
        y: Int,
        windowWidth: Int,
        label: String,
        value: Float,
        min: Float,
        max: Float,
        display: String,
    ) {
        graphics.fill(windowX + 10, y, windowX + windowWidth - 10, y + 20, windowInner)
        graphics.text(font, label, windowX + 16, y + 5, textColor, false)
        val sliderX = windowX + 78
        val sliderWidth = windowWidth - 128
        val sliderY = y + 13
        val progress = ((value - min) / (max - min)).coerceIn(0f, 1f)
        graphics.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + 3, 0xFF44353C.toInt())
        graphics.fill(sliderX, sliderY, sliderX + (sliderWidth * progress).roundToInt(), sliderY + 3, accent)
        val knobX = sliderX + (sliderWidth * progress).roundToInt()
        graphics.fill(knobX - 2, sliderY - 4, knobX + 3, sliderY + 7, textColor)
        graphics.text(font, display, windowX + windowWidth - 16 - font.width(display), y + 5, accentBright, false)
    }

    private fun drawActionSetting(
        graphics: GuiGraphicsExtractor,
        y: Int,
        windowWidth: Int,
        label: String,
        action: String,
        mouseX: Int,
        mouseY: Int,
    ) {
        graphics.fill(windowX + 10, y, windowX + windowWidth - 10, y + 20, windowInner)
        graphics.text(font, label, windowX + 16, y + 6, textColor, false)
        val buttonX = windowX + windowWidth - 62
        val hovered = mouseX in buttonX until (windowX + windowWidth - 16) && mouseY in (y + 3) until (y + 17)
        graphics.fill(buttonX, y + 3, windowX + windowWidth - 16, y + 17, if (hovered) accent else accentMuted)
        graphics.centeredText(font, action, (buttonX + windowX + windowWidth - 16) / 2, y + 6, textColor)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val mouseX = event.x.toInt()
        val mouseY = event.y.toInt()

        openFeature?.let { feature ->
            val (windowWidth, _) = windowSize(feature)
            if (event.button() == 0 && mouseX in (windowX + windowWidth - 21) until (windowX + windowWidth - 5) &&
                mouseY in (windowY + 4) until (windowY + 20)
            ) {
                UiStyle.playClick(0.9f)
                closeFeatureWindow()
                return true
            }
            if (event.button() == 0 && mouseX in windowX until (windowX + windowWidth - 23) && mouseY in windowY until (windowY + 24)) {
                draggingWindow = true
                windowDragX = mouseX - windowX
                windowDragY = mouseY - windowY
                return true
            }
            if (event.button() == 0 && handleWindowClick(feature, mouseX, mouseY)) {
                UiStyle.playClick()
                return true
            }
            return super.mouseClicked(event, isDoubleClick)
        }

        featureAt(mouseX, mouseY)?.let { feature ->
            when (event.button()) {
                0 -> {
                    if (feature == FeatureEntry.CLICK_GUI) {
                        openFeatureWindow(feature)
                    } else {
                        feature.setEnabled(!feature.enabled())
                    }
                    ModConfig.save()
                    UiStyle.playClick()
                    return true
                }
                1 -> {
                    if (feature.hasOptions) {
                        openFeatureWindow(feature)
                        UiStyle.playClick(1.1f)
                    }
                    return true
                }
            }
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    private fun handleWindowClick(feature: FeatureEntry, mouseX: Int, mouseY: Int): Boolean {
        when (feature) {
            FeatureEntry.HIDE_LINE -> when (mouseY) {
                in (windowY + 30)..(windowY + 49) -> ModConfig.values.hideFishingLine = !ModConfig.values.hideFishingLine
                else -> return false
            }
            FeatureEntry.LOUDER_CATCH -> when (mouseY) {
                in (windowY + 30)..(windowY + 49) -> ModConfig.values.louderCatch = !ModConfig.values.louderCatch
                in (windowY + 52)..(windowY + 71) -> cycleSound()
                in (windowY + 74)..(windowY + 93) -> {
                    draggingSlider = DraggingSlider.VOLUME
                    updateSlider(mouseX)
                }
                in (windowY + 96)..(windowY + 115) -> {
                    draggingSlider = DraggingSlider.PITCH
                    updateSlider(mouseX)
                }
                in (windowY + 118)..(windowY + 137) -> LouderCatch.playConfigured()
                else -> return false
            }
            FeatureEntry.PARTY_HELPER -> when (mouseY) {
                in (windowY + 30)..(windowY + 49) -> ModConfig.values.fishingPartyHelper = !ModConfig.values.fishingPartyHelper
                in (windowY + 52)..(windowY + 71) -> ModConfig.values.autoKick = !ModConfig.values.autoKick
                in (windowY + 74)..(windowY + 93) -> if (ModConfig.values.autoKick) {
                    ModConfig.values.kickNoLootingV = !ModConfig.values.kickNoLootingV
                } else return false
                in (windowY + 96)..(windowY + 115) -> if (ModConfig.values.autoKick) {
                    ModConfig.values.kickCantJawbus = !ModConfig.values.kickCantJawbus
                } else return false
                else -> return false
            }
            FeatureEntry.MM_PARTY -> when (mouseY) {
                in (windowY + 30)..(windowY + 49) -> FeatureEntry.MM_PARTY.setEnabled(!FeatureEntry.MM_PARTY.enabled())
                in (windowY + 52)..(windowY + 71) -> FishingPartyHelper.refreshPartyReadiness(force = true)
                else -> return false
            }
            FeatureEntry.JAWBUS_FINISHER -> when (mouseY) {
                in (windowY + 30)..(windowY + 49) -> ModConfig.values.jawbusFinisherEnabled = !ModConfig.values.jawbusFinisherEnabled
                in (windowY + 52)..(windowY + 71) -> cycleFinisherHealth()
                in (windowY + 74)..(windowY + 93) -> if (ModConfig.values.jawbusFinisherEnabled) {
                    ModConfig.values.jawbusFinisherPartyMessage = !ModConfig.values.jawbusFinisherPartyMessage
                } else return false
                in (windowY + 118)..(windowY + 137) -> JawbusFinisherHelper.debugPreview()
                else -> return false
            }
            FeatureEntry.JAWBUS_FINDER -> when (mouseY) {
                in (windowY + 30)..(windowY + 49) -> ModConfig.values.jawbusFinder = !ModConfig.values.jawbusFinder
                in (windowY + 52)..(windowY + 71) -> if (ModConfig.values.jawbusFinder) {
                    ModConfig.values.deathMessageDetection = !ModConfig.values.deathMessageDetection
                } else return false
                else -> return false
            }
            FeatureEntry.MEDIA_PLAYER -> when (mouseY) {
                in (windowY + 30)..(windowY + 49) -> {
                    ModConfig.values.mediaPlayer = !ModConfig.values.mediaPlayer
                    MediaPlayer.onEnabledChanged(ModConfig.values.mediaPlayer)
                }
                in (windowY + 52)..(windowY + 71) -> if (ModConfig.values.mediaPlayer) {
                    ModConfig.values.mediaHud = !ModConfig.values.mediaHud
                } else return false
                in (windowY + 74)..(windowY + 93) -> if (ModConfig.values.mediaPlayer) {
                    ModConfig.values.mediaAutoplay = !ModConfig.values.mediaAutoplay
                } else return false
                in (windowY + 96)..(windowY + 115) -> if (ModConfig.values.mediaPlayer) {
                    draggingSlider = DraggingSlider.MEDIA_VOLUME
                    updateSlider(mouseX)
                } else return false
                in (windowY + 118)..(windowY + 137) -> if (ModConfig.values.mediaPlayer) {
                    if (MediaPlayer.currentInfo() == null) MediaPlayer.openScreen(this) else MediaPlayer.togglePause()
                } else return false
                in (windowY + 140)..(windowY + 159) -> if (ModConfig.values.mediaPlayer) {
                    MediaPlayer.openScreen(this)
                } else return false
                else -> return false
            }
            FeatureEntry.PARTY_COMMANDS -> {
                if (mouseY in (windowY + 30)..(windowY + 49)) {
                    ModConfig.values.partyCommandsEnabled = !ModConfig.values.partyCommandsEnabled
                } else {
                    val commandIndex = PartyCommands.definitions.indices.firstOrNull { index ->
                        mouseY in (windowY + 52 + index * 44)..(windowY + 71 + index * 44)
                    } ?: return false
                    if (!ModConfig.values.partyCommandsEnabled) return false
                    val definition = PartyCommands.definitions[commandIndex]
                    val setting = PartyCommands.setting(definition)
                    setting.enabled = !setting.enabled
                }
            }
            FeatureEntry.CLICK_GUI -> when (mouseY) {
                in (windowY + 30)..(windowY + 49) -> ModConfig.values.clickGuiSound = !ModConfig.values.clickGuiSound
                in (windowY + 52)..(windowY + 71) -> UiStyle.cycleAccent()
                in (windowY + 74)..(windowY + 93) -> UiStyle.cycleSorting()
                in (windowY + 96)..(windowY + 115) -> minecraft.setScreen(HudEditorScreen(this))
                in (windowY + 118)..(windowY + 137) -> UiStyle.reset()
                else -> return false
            }
        }
        ModConfig.save()
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (draggingWindow) {
            val feature = openFeature ?: return true
            val (windowWidth, windowHeight) = windowSize(feature)
            windowX = (event.x.toInt() - windowDragX).coerceIn(4, (width - windowWidth - 4).coerceAtLeast(4))
            windowY = (event.y.toInt() - windowDragY).coerceIn(4, (height - windowHeight - 4).coerceAtLeast(4))
            return true
        }
        if (draggingSlider != null) {
            updateSlider(event.x.toInt())
            return true
        }
        return super.mouseDragged(event, deltaX, deltaY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val feature = openFeature ?: return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        val (windowWidth, windowHeight) = windowSize(feature)
        if (mouseX.toInt() !in windowX until (windowX + windowWidth) ||
            mouseY.toInt() !in windowY until (windowY + windowHeight)
        ) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)

        if (handleWindowScroll(feature, mouseX.toInt(), mouseY.toInt(), vertical)) {
            UiStyle.playClick(1.15f)
            ModConfig.save()
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (draggingWindow || draggingSlider != null) {
            draggingWindow = false
            draggingSlider = null
            ModConfig.save()
            return true
        }
        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key == GLFW.GLFW_KEY_ESCAPE && openFeature != null) {
            closeFeatureWindow()
            return true
        }
        return super.keyPressed(event)
    }

    private fun openFeatureWindow(feature: FeatureEntry) {
        openFeature = feature
        val (windowWidth, windowHeight) = windowSize(feature)
        windowX = (width - windowWidth) / 2
        windowY = (height - windowHeight) / 2
    }

    private fun closeFeatureWindow() {
        draggingWindow = false
        draggingSlider = null
        finisherMessageBox.visible = false
        partyAliasBoxes.values.forEach { it.visible = false }
        PartyCommands.ensureSettings()
        PartyCommands.definitions.forEach { definition ->
            partyAliasBoxes[definition.id]?.setValue(PartyCommands.alias(definition))
        }
        openFeature = null
        ModConfig.save()
    }

    private fun cycleSound(direction: Int = 1) {
        val choices = LouderCatch.choices
        val current = choices.indexOfFirst { it.label == ModConfig.values.catchSound }.coerceAtLeast(0)
        ModConfig.values.catchSound = choices[(current + direction).mod(choices.size)].label
    }

    private fun cycleFinisherHealth() {
        val choices = (5..50 step 5).toList()
        val current = choices.indexOf(ModConfig.values.jawbusFinisherHealth).coerceAtLeast(0)
        ModConfig.values.jawbusFinisherHealth = choices[(current + 1) % choices.size]
    }

    private fun updateSlider(mouseX: Int) {
        val feature = openFeature ?: return
        val (windowWidth, _) = windowSize(feature)
        val sliderX = windowX + 78
        val sliderWidth = windowWidth - 128
        val progress = ((mouseX - sliderX) / sliderWidth.toFloat()).coerceIn(0f, 1f)
        when (draggingSlider) {
            DraggingSlider.VOLUME -> ModConfig.values.catchVolume = ((0.1f + progress * 19.9f) * 10).roundToInt() / 10f
            DraggingSlider.PITCH -> ModConfig.values.catchPitch = ((0.5f + progress * 1.5f) * 20).roundToInt() / 20f
            DraggingSlider.MEDIA_VOLUME -> MediaPlayer.setVolume((progress * 100).roundToInt() / 100f, save = false)
            null -> Unit
        }
    }

    private fun handleWindowScroll(feature: FeatureEntry, mouseX: Int, mouseY: Int, delta: Double): Boolean {
        if (delta == 0.0) return false
        val direction = if (delta > 0.0) 1 else -1
        val inRow = { offset: Int ->
            mouseX in (windowX + 10) until (windowX + windowSize(feature).first - 10) &&
                mouseY in (windowY + offset) until (windowY + offset + 20)
        }
        when (feature) {
            FeatureEntry.LOUDER_CATCH -> when {
                inRow(52) -> cycleSound(direction)
                inRow(74) -> ModConfig.values.catchVolume = stepValue(ModConfig.values.catchVolume, direction, 0.1f, 20f, 0.1f)
                inRow(96) -> ModConfig.values.catchPitch = stepValue(ModConfig.values.catchPitch, direction, 0.5f, 2f, 0.05f)
                else -> return false
            }
            FeatureEntry.JAWBUS_FINISHER -> if (inRow(52)) {
                ModConfig.values.jawbusFinisherHealth =
                    (ModConfig.values.jawbusFinisherHealth + direction * 5).coerceIn(5, 50)
            } else return false
            FeatureEntry.MEDIA_PLAYER -> if (inRow(96) && ModConfig.values.mediaPlayer) {
                MediaPlayer.setVolume(stepValue(ModConfig.values.mediaVolume, direction, 0f, 1f, 0.05f), save = false)
            } else return false
            FeatureEntry.CLICK_GUI -> when {
                inRow(52) -> UiStyle.cycleAccent(direction)
                inRow(74) -> UiStyle.cycleSorting(direction)
                else -> return false
            }
            else -> return false
        }
        return true
    }

    private fun featureAt(mouseX: Int, mouseY: Int): FeatureEntry? {
        panelLayouts().forEach { panel ->
            orderedFeatures(panel.category).forEachIndexed { index, feature ->
                val y = panel.y + 22 + index * 16
                if (mouseX in panel.x until (panel.x + panel.width) && mouseY in y until (y + 16)) return feature
            }
        }
        return null
    }

    private fun panelLayouts(): List<PanelLayout> {
        val panelWidth = 110
        val gap = 10
        val startX = 10
        val top = 10
        return categories.mapIndexed { index, category ->
            PanelLayout(category, startX + index * (panelWidth + gap), top, panelWidth)
        }
    }

    private fun orderedFeatures(category: FeatureCategory): List<FeatureEntry> = when (ModConfig.values.clickGuiSorting) {
        "A-Z Sorting" -> category.features.sortedBy { it.title.lowercase() }
        "Width Sorting" -> category.features.sortedByDescending { font.width(it.title) }
        else -> category.features
    }

    private fun windowSize(feature: FeatureEntry): Pair<Int, Int> = when (feature) {
        FeatureEntry.LOUDER_CATCH -> 230 to 142
        FeatureEntry.PARTY_HELPER -> 230 to 120
        FeatureEntry.MM_PARTY -> 230 to 76
        FeatureEntry.JAWBUS_FINISHER -> 310 to 142
        FeatureEntry.JAWBUS_FINDER -> 230 to 76
        FeatureEntry.MEDIA_PLAYER -> 230 to 164
        FeatureEntry.PARTY_COMMANDS -> 250 to (54 + PartyCommands.definitions.size * 44)
        FeatureEntry.CLICK_GUI -> 230 to 142
        FeatureEntry.HIDE_LINE -> 230 to 54
    }

    override fun onClose() {
        PartyCommands.ensureSettings()
        ModConfig.save()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
    override fun isInGameUi(): Boolean = true

    companion object {
        internal fun stepValue(value: Float, direction: Int, min: Float, max: Float, step: Float): Float {
            val stepped = round((value + direction.coerceIn(-1, 1) * step) / step) * step
            return stepped.coerceIn(min, max)
        }
    }
}
