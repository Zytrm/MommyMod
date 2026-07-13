package com.zytrm.mommymods.mixin;

import com.zytrm.mommymods.feature.JawbusFinder;
import com.zytrm.mommymods.feature.JawbusFinisherHelper;
import com.zytrm.mommymods.feature.MediaPlayer;
import com.zytrm.mommymods.feature.PartyReadinessHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void mommymods$beginHudFrame(
        GuiGraphicsExtractor graphics,
        DeltaTracker deltaTracker,
        CallbackInfo ci
    ) {
        PartyReadinessHud.beginFrame();
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("TAIL"))
    private void mommymods$renderPartyReadinessSidebar(
        GuiGraphicsExtractor graphics,
        Objective objective,
        CallbackInfo ci
    ) {
        PartyReadinessHud.renderSidebar(graphics, objective);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mommymods$renderJawbusAlert(
        GuiGraphicsExtractor graphics,
        DeltaTracker deltaTracker,
        CallbackInfo ci
    ) {
        JawbusFinder.render(graphics);
        JawbusFinisherHelper.render(graphics);
        PartyReadinessHud.renderFallback(graphics);
        MediaPlayer.renderHud(graphics);
    }
}
