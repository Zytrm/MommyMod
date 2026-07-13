package com.zytrm.mommymods.ui

import com.zytrm.mommymods.config.ModConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class HudEditorScreen(private val parent: Screen?) : Screen(Component.literal("MommyMods HUD Editor")) {
    private data class Preview(val element: HudElement, val label: String, val width: Int, val height: Int)

    private val previews = listOf(
        Preview(HudElement.JAWBUS, "JAWBUS ALERT", 236, 42),
        Preview(HudElement.MEDIA, "AURA PLAYER HUD", 196, 38),
    )
    private var dragging: Preview? = null
    private var dragOffsetX = 0
    private var dragOffsetY = 0

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xB20A070B.toInt())
        graphics.centeredText(font, "HUD EDITOR", width / 2, 10, 0xFFFFE8F0.toInt())
        graphics.centeredText(font, "Drag an element to reposition it", width / 2, 23, 0xFFCDB2BE.toInt())

        previews.forEach { preview -> drawPreview(graphics, preview, mouseX, mouseY) }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    private fun drawPreview(graphics: GuiGraphicsExtractor, preview: Preview, mouseX: Int, mouseY: Int) {
        val (x, y) = HudLayout.position(preview.element, preview.width, preview.height, width, height)
        val hovered = mouseX in x until (x + preview.width) && mouseY in y until (y + preview.height)
        graphics.fill(x, y, x + preview.width, y + preview.height, if (hovered) 0xEA21131A.toInt() else 0xE0110C12.toInt())
        graphics.fill(x, y, x + preview.width, y + 2, UiStyle.accent)
        graphics.fill(x, y + preview.height - 2, x + preview.width, y + preview.height, UiStyle.accent)
        graphics.centeredText(font, preview.label, x + preview.width / 2, y + 8, 0xFFFFE8F0.toInt())
        graphics.centeredText(font, "Drag to move", x + preview.width / 2, y + 23, 0xFFCDB2BE.toInt())
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != 0) return super.mouseClicked(event, isDoubleClick)
        val mouseX = event.x.toInt()
        val mouseY = event.y.toInt()
        previews.asReversed().firstOrNull { preview ->
            val (x, y) = HudLayout.position(preview.element, preview.width, preview.height, width, height)
            mouseX in x until (x + preview.width) && mouseY in y until (y + preview.height)
        }?.let { preview ->
            val (x, y) = HudLayout.position(preview.element, preview.width, preview.height, width, height)
            dragging = preview
            dragOffsetX = mouseX - x
            dragOffsetY = mouseY - y
            UiStyle.playClick(1.1f)
            return true
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val preview = dragging ?: return super.mouseDragged(event, deltaX, deltaY)
        val x = (event.x.toInt() - dragOffsetX).coerceIn(0, (width - preview.width).coerceAtLeast(0))
        val y = (event.y.toInt() - dragOffsetY).coerceIn(0, (height - preview.height).coerceAtLeast(0))
        HudLayout.setPosition(preview.element, x, y, preview.width, preview.height, width, height)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging != null) {
            dragging = null
            ModConfig.save()
            return true
        }
        return super.mouseReleased(event)
    }

    override fun onClose() {
        dragging = null
        ModConfig.save()
        minecraft.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
    override fun isInGameUi(): Boolean = true
}
