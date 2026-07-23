//#if MC >= 1.21.8
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.DialogWarning;
//$$ import carpet.fga.FGASettings;
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//$$ import com.mojang.serialization.Codec;
//$$ import com.mojang.serialization.MapCodec;
//$$ import net.minecraft.nbt.CompoundTag;
//$$ import net.minecraft.network.chat.ClickEvent;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$
//$$ import java.util.function.Function;
//$$
//$$ @Mixin(ClickEvent.class)
//$$ public interface DialogWarningClickEventMixin {
//$$     @WrapOperation(
//$$         method = "<clinit>",
//$$         at = @At(
//$$             value = "INVOKE",
//$$             target = "Lcom/mojang/serialization/Codec;dispatch(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
//$$         )
//$$     )
//$$     private static <A> Codec<ClickEvent> carpetFga$customCodec(
//$$             Codec<ClickEvent> instance,
//$$             String typeKey,
//$$             Function<ClickEvent, ? extends A> type,
//$$             Function<? super A, ? extends MapCodec<ClickEvent>> codec,
//$$             Operation<Codec<ClickEvent>> original
//$$     ) {
//$$         return original.call(instance, typeKey, type, codec).xmap(Function.identity(), clickEvent -> {
//$$             if (FGASettings.removeDialogWarning
//$$                     && clickEvent instanceof ClickEvent.RunCommand(String command)) {
//$$                 CompoundTag tag = new CompoundTag();
//$$                 tag.putString(DialogWarning.COMMAND_KEY, command);
//$$                 return DialogWarning.customClickAction(tag);
//$$             }
//$$             return clickEvent;
//$$         });
//$$     }
//$$ }
//#endif
