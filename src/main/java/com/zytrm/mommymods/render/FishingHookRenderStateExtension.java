package com.zytrm.mommymods.render;

public interface FishingHookRenderStateExtension {
    boolean mommymods$isLocalOwner();

    boolean mommymods$isSubmerged();

    void mommymods$setVisibilityState(boolean localOwner, boolean submerged);
}
