package com.zytrm.mommymods.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zytrm.mommymods.config.ModConfig;
import com.zytrm.mommymods.feature.BobberVisibility;
import com.zytrm.mommymods.render.FishingHookRenderStateExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/projectile/FishingHook;Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;F)V",
        at = @At("TAIL")
    )
    private void mommymods$captureBobberVisibility(
        FishingHook hook,
        FishingHookRenderState state,
        float partialTick,
        CallbackInfo ci
    ) {
        boolean localOwner = hook.getPlayerOwner() == Minecraft.getInstance().player;
        boolean submerged = hook.isInWater() || hook.isInLava();
        ((FishingHookRenderStateExtension) state).mommymods$setVisibilityState(localOwner, submerged);
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mommymods$hideBobber(
        FishingHookRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CameraRenderState cameraState,
        CallbackInfo ci
    ) {
        FishingHookRenderStateExtension visibility = (FishingHookRenderStateExtension) state;
        if (BobberVisibility.shouldHideBobber(
            visibility.mommymods$isLocalOwner(),
            visibility.mommymods$isSubmerged()
        )) {
            ci.cancel();
        }
    }

    @Redirect(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V",
            ordinal = 1
        )
    )
    private void mommymods$renderFishingLine(
        SubmitNodeCollector collector,
        PoseStack poseStack,
        RenderType renderType,
        SubmitNodeCollector.CustomGeometryRenderer renderer
    ) {
        if (!ModConfig.INSTANCE.getValues().getHideFishingLine()) {
            collector.submitCustomGeometry(poseStack, renderType, renderer);
        }
    }
}
