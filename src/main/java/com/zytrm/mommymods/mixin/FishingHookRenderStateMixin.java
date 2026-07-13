package com.zytrm.mommymods.mixin;

import com.zytrm.mommymods.render.FishingHookRenderStateExtension;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FishingHookRenderState.class)
public abstract class FishingHookRenderStateMixin implements FishingHookRenderStateExtension {
    @Unique
    private boolean mommymods$localOwner;

    @Unique
    private boolean mommymods$submerged;

    @Override
    public boolean mommymods$isLocalOwner() {
        return mommymods$localOwner;
    }

    @Override
    public boolean mommymods$isSubmerged() {
        return mommymods$submerged;
    }

    @Override
    public void mommymods$setVisibilityState(boolean localOwner, boolean submerged) {
        mommymods$localOwner = localOwner;
        mommymods$submerged = submerged;
    }
}
