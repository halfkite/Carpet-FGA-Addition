//#if MC >= 1.21.8
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.DialogWarning;
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//$$ import com.mojang.datafixers.util.Pair;
//$$ import com.mojang.serialization.Codec;
//$$ import com.mojang.serialization.DataResult;
//$$ import com.mojang.serialization.DynamicOps;
//$$ import com.mojang.serialization.MapCodec;
//$$ import net.minecraft.server.dialog.Dialog;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$
//$$ import java.util.function.Function;
//$$
//$$ @Mixin(Dialog.class)
//$$ public interface DialogWarningDialogMixin {
//$$     @WrapOperation(
//$$         method = "<clinit>",
//$$         at = @At(
//$$             value = "INVOKE",
//$$             target = "Lcom/mojang/serialization/Codec;dispatch(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
//$$         )
//$$     )
//$$     private static <A> Codec<Dialog> carpetFga$customCodec(
//$$             Codec<Dialog> instance,
//$$             Function<? super Dialog, ? extends A> type,
//$$             Function<? super A, ? extends MapCodec<? extends Dialog>> codec,
//$$             Operation<Codec<Dialog>> original
//$$     ) {
//$$         Codec<Dialog> originalCodec = original.call(instance, type, codec);
//$$         return new Codec<>() {
//$$             @Override
//$$             public <T> DataResult<Pair<Dialog, T>> decode(DynamicOps<T> ops, T input) {
//$$                 return originalCodec.decode(ops, input);
//$$             }
//$$
//$$             @Override
//$$             public <T> DataResult<T> encode(Dialog input, DynamicOps<T> ops, T prefix) {
//$$                 Dialog previous = DialogWarning.DIALOG_SCOPE.get();
//$$                 DialogWarning.DIALOG_SCOPE.set(input);
//$$                 try {
//$$                     return originalCodec.encode(input, ops, prefix);
//$$                 } finally {
//$$                     if (previous == null) {
//$$                         DialogWarning.DIALOG_SCOPE.remove();
//$$                     } else {
//$$                         DialogWarning.DIALOG_SCOPE.set(previous);
//$$                     }
//$$                 }
//$$             }
//$$         };
//$$     }
//$$ }
//#endif
