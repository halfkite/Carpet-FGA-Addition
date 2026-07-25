//#if MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
//#if MC >= 1.20.5 && MC < 1.21.5
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
//#elseif MC == 1.21.5
//$$ import com.mojang.serialization.Codec;
//$$ import com.mojang.serialization.DynamicOps;
//$$ import net.minecraft.nbt.CompoundTag;
//$$ import net.minecraft.nbt.Tag;
//#elseif MC < 1.20.5
//$$ import net.minecraft.nbt.CompoundTag;
//#else
//$$ import com.mojang.serialization.Codec;
//$$ import net.minecraft.world.level.storage.ValueInput;
//$$ import net.minecraft.world.level.storage.ValueOutput;
//#endif
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    private static final String EXTENDED_COUNT_KEY = "carpet-fga-addition:ExtendedCount";
    private static final int VANILLA_SAVED_COUNT_LIMIT = 99;

        // Avoid @Redirect on isMergable: Carpet also redirects getMaxStackSize there and hard-fails on conflict.
    @Shadow
    private int age;
    @Shadow
    private int pickupDelay;

    @Inject(method = "isMergable", at = @At("RETURN"), cancellable = true)
    private void carpetFga$stackLimitForMergable(CallbackInfoReturnable<Boolean> cir) {
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack stack = self.getItem();
        int limit = FGASettings.effectiveDroppedItemStackLimit(stack);
        if (cir.getReturnValueZ()) {
            if (stack.getCount() >= limit) {
                cir.setReturnValue(false);
            }
            return;
        }
        if (self.isAlive()
                && pickupDelay != 32767
                && age != -32768
                && age < 6000
                && stack.getCount() < limit) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(
            method = "areMergable",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I")
    )
    private static int carpetFga$stackLimitForTotal(ItemStack stack) {
        return FGASettings.effectiveDroppedItemStackLimit(stack);
    }

    @Redirect(
            method = "merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I")
    )
    private static int carpetFga$stackLimitForMerge(ItemStack stack) {
        return FGASettings.effectiveDroppedItemStackLimit(stack);
    }

    @ModifyConstant(
            method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V",
            constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 64)
    )
    private static int carpetFga$stackLimitForEntityMerge(int vanillaLimit, ItemEntity entity,
                                                           ItemStack destination, ItemStack source) {
        return FGASettings.effectiveDroppedItemStackLimit(destination);
    }

    //#if MC >= 1.20.5 && MC < 1.21.6
    //#if MC >= 1.21.5
    //$$ @Redirect(
    //$$         method = "addAdditionalSaveData",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/nbt/CompoundTag;store(Ljava/lang/String;Lcom/mojang/serialization/Codec;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)V"
    //$$         )
    //$$ )
    //$$ private void carpetFga$saveVanillaCompatibleCount(CompoundTag tag, String key,
    //$$                                                       Codec<Object> codec, DynamicOps<Tag> ops,
    //$$                                                       Object value) {
    //$$     ItemStack stack = (ItemStack) value;
    //$$     ItemStack savedStack = stack.getCount() > VANILLA_SAVED_COUNT_LIMIT
    //$$             ? stack.copyWithCount(VANILLA_SAVED_COUNT_LIMIT)
    //$$             : stack;
    //$$     tag.store(key, codec, ops, savedStack);
    //$$ }
    //#else
    @Redirect(
            method = "addAdditionalSaveData",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;save(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/Tag;")
    )
    private Tag carpetFga$saveVanillaCompatibleCount(ItemStack stack, HolderLookup.Provider provider) {
        ItemStack savedStack = stack.getCount() > VANILLA_SAVED_COUNT_LIMIT
                ? stack.copyWithCount(VANILLA_SAVED_COUNT_LIMIT)
                : stack;
        return savedStack.save(provider);
    }
    //#endif

    @org.spongepowered.asm.mixin.injection.Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void carpetFga$saveExtendedCount(CompoundTag tag, CallbackInfo ci) {
        int count = ((ItemEntity) (Object) this).getItem().getCount();
        if (count > VANILLA_SAVED_COUNT_LIMIT) {
            tag.putInt(EXTENDED_COUNT_KEY, count);
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void carpetFga$loadExtendedCount(CompoundTag tag, CallbackInfo ci) {
        if (!tag.contains(EXTENDED_COUNT_KEY)) {
            return;
        }
        int count =
                //#if MC >= 1.21.5
                //$$ tag.getInt(EXTENDED_COUNT_KEY).orElse(0);
                //#else
                tag.getInt(EXTENDED_COUNT_KEY);
                //#endif
        if (count > VANILLA_SAVED_COUNT_LIMIT && count <= 8192) {
            ((ItemEntity) (Object) this).getItem().setCount(count);
        }
    }
    //#elseif MC < 1.20.5
    //$$ @Redirect(
    //$$         method = "addAdditionalSaveData",
    //$$         at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;")
    //$$ )
    //$$ private CompoundTag carpetFga$saveVanillaCompatibleCount(ItemStack stack, CompoundTag tag) {
    //$$     ItemStack savedStack = stack.copy();
    //$$     if (savedStack.getCount() > VANILLA_SAVED_COUNT_LIMIT) {
    //$$         savedStack.setCount(VANILLA_SAVED_COUNT_LIMIT);
    //$$     }
    //$$     return savedStack.save(tag);
    //$$ }
    //$$
    //$$ @org.spongepowered.asm.mixin.injection.Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    //$$ private void carpetFga$saveExtendedCount(CompoundTag tag, CallbackInfo ci) {
    //$$     int count = ((ItemEntity) (Object) this).getItem().getCount();
    //$$     if (count > VANILLA_SAVED_COUNT_LIMIT) {
    //$$         tag.putInt(EXTENDED_COUNT_KEY, count);
    //$$     }
    //$$ }
    //$$
    //$$ @org.spongepowered.asm.mixin.injection.Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    //$$ private void carpetFga$loadExtendedCount(CompoundTag tag, CallbackInfo ci) {
    //$$     if (tag.contains(EXTENDED_COUNT_KEY)) {
    //$$         int count = tag.getInt(EXTENDED_COUNT_KEY);
    //$$         if (count > VANILLA_SAVED_COUNT_LIMIT && count <= 8192) {
    //$$             ((ItemEntity) (Object) this).getItem().setCount(count);
    //$$         }
    //$$     }
    //$$ }
    //#else
    //$$ @Redirect(
    //$$         method = "addAdditionalSaveData",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/world/level/storage/ValueOutput;store(Ljava/lang/String;Lcom/mojang/serialization/Codec;Ljava/lang/Object;)V"
    //$$         )
    //$$ )
    //$$ private void carpetFga$saveVanillaCompatibleCount(ValueOutput output, String key,
    //$$                                                       Codec<Object> codec, Object value) {
    //$$     ItemStack stack = (ItemStack) value;
    //$$     ItemStack savedStack = stack.getCount() > VANILLA_SAVED_COUNT_LIMIT
    //$$             ? stack.copyWithCount(VANILLA_SAVED_COUNT_LIMIT)
    //$$             : stack;
    //$$     output.store(key, codec, savedStack);
    //$$ }
    //$$
    //$$ @org.spongepowered.asm.mixin.injection.Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    //$$ private void carpetFga$saveExtendedCount(ValueOutput output, CallbackInfo ci) {
    //$$     int count = ((ItemEntity) (Object) this).getItem().getCount();
    //$$     if (count > VANILLA_SAVED_COUNT_LIMIT) {
    //$$         output.putInt(EXTENDED_COUNT_KEY, count);
    //$$     }
    //$$ }
    //$$
    //$$ @org.spongepowered.asm.mixin.injection.Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    //$$ private void carpetFga$loadExtendedCount(ValueInput input, CallbackInfo ci) {
    //$$     int count = input.getIntOr(EXTENDED_COUNT_KEY, 0);
    //$$     if (count > VANILLA_SAVED_COUNT_LIMIT && count <= 8192) {
    //$$         ((ItemEntity) (Object) this).getItem().setCount(count);
    //$$     }
    //$$ }
    //#endif

    @Redirect(
            method = "mergeWithNeighbours",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private List<ItemEntity> carpetFga$mergeDistance(
            Level level, Class<ItemEntity> entityClass, AABB vanillaBox,
            Predicate<? super ItemEntity> predicate
    ) {
        Entity self = (Entity) (Object) this;
        double distance = FGASettings.effectiveDroppedItemMergeDistance();
        AABB box = self.getBoundingBox().inflate(distance, 0.0D, distance);
        return level.getEntitiesOfClass(entityClass, box, predicate);
    }
}
//#endif
