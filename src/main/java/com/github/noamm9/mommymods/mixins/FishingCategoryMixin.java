package com.github.noamm9.mommymods.mixins;

import com.github.noamm9.mommymods.MommyMods;
import com.github.noamm9.ui.clickgui.enums.CategoryType;
import kotlin.enums.EnumEntriesKt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;

@Mixin(CategoryType.class)
public class FishingCategoryMixin {
    @Shadow @Final @Mutable
    private static CategoryType[] $VALUES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void mommymods$addFishingCategory(CallbackInfo ci) {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            ArrayList<CategoryType> categories = new ArrayList<>(Arrays.asList($VALUES));
            CategoryType fishing = (CategoryType) unsafe.allocateInstance(CategoryType.class);

            Field nameField = Enum.class.getDeclaredField("name");
            Field ordinalField = Enum.class.getDeclaredField("ordinal");
            long ordinalOffset = unsafe.objectFieldOffset(ordinalField);
            unsafe.putObject(fishing, unsafe.objectFieldOffset(nameField), "FISHING");
            unsafe.putInt(fishing, ordinalOffset, 0);

            for (int index = 0; index < categories.size(); index++) {
                unsafe.putInt(categories.get(index), ordinalOffset, index + 1);
            }
            categories.add(0, fishing);
            $VALUES = categories.toArray(new CategoryType[0]);

            Field entriesField = CategoryType.class.getDeclaredField("$ENTRIES");
            Object base = unsafe.staticFieldBase(entriesField);
            long offset = unsafe.staticFieldOffset(entriesField);
            unsafe.putObject(base, offset, EnumEntriesKt.enumEntries($VALUES));
        } catch (ReflectiveOperationException exception) {
            MommyMods.logger.error("Could not register the Fishing settings category", exception);
        }
    }
}
