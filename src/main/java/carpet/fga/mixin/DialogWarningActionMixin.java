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
//$$ import net.minecraft.nbt.ListTag;
//$$ import net.minecraft.nbt.StringTag;
//$$ import net.minecraft.network.chat.ClickEvent;
//$$ import net.minecraft.server.dialog.Dialog;
//$$ import net.minecraft.server.dialog.Input;
//$$ import net.minecraft.server.dialog.action.Action;
//$$ import net.minecraft.server.dialog.action.CommandTemplate;
//$$ import net.minecraft.server.dialog.action.ParsedTemplate;
//$$ import net.minecraft.server.dialog.action.StaticAction;
//$$ import net.minecraft.server.dialog.input.BooleanInput;
//$$ import net.minecraft.server.dialog.input.InputControl;
//$$ import net.minecraft.server.dialog.input.TextInput;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$
//$$ import java.util.function.Function;
//$$
//$$ @Mixin(Action.class)
//$$ public interface DialogWarningActionMixin {
//$$     @WrapOperation(
//$$         method = "<clinit>",
//$$         at = @At(
//$$             value = "INVOKE",
//$$             target = "Lcom/mojang/serialization/Codec;dispatch(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
//$$         )
//$$     )
//$$     private static <A> Codec<Action> carpetFga$customCodec(
//$$             Codec<Action> instance,
//$$             Function<Action, ? extends A> type,
//$$             Function<? super A, ? extends MapCodec<Action>> codec,
//$$             Operation<Codec<Action>> original
//$$     ) {
//$$         return original.call(instance, type, codec).xmap(Function.identity(), action -> {
//$$             if (!FGASettings.removeDialogWarning) {
//$$                 return action;
//$$             }
//$$             if (action instanceof CommandTemplate(ParsedTemplate template)) {
//$$                 CompoundTag tag = new CompoundTag();
//$$                 tag.putString(DialogWarning.COMMAND_KEY, ((DialogWarningParsedTemplateAccessor) template).carpetFga$getRaw());
//$$                 tag.putBoolean(DialogWarning.DYNAMIC_KEY, true);
//$$                 Dialog dialog = DialogWarning.DIALOG_SCOPE.get();
//$$                 CompoundTag booleanInputs = new CompoundTag();
//$$                 ListTag textInputs = new ListTag();
//$$                 for (Input input : dialog.common().inputs()) {
//$$                     InputControl control = input.control();
//$$                     switch (control) {
//$$                         case BooleanInput booleanInput -> {
//$$                             CompoundTag booleanTag = new CompoundTag();
//$$                             booleanTag.putString("true", booleanInput.onTrue());
//$$                             booleanTag.putString("false", booleanInput.onFalse());
//$$                             booleanInputs.put(input.key(), booleanTag);
//$$                         }
//$$                         case TextInput ignored -> textInputs.add(StringTag.valueOf(input.key()));
//$$                         default -> {
//$$                         }
//$$                     }
//$$                 }
//$$                 tag.put(DialogWarning.BOOLEAN_TAGS_KEY, booleanInputs);
//$$                 tag.put(DialogWarning.STRING_INPUT_KEY, textInputs);
//$$                 return DialogWarning.customDialogAction(tag);
//$$             }
//$$             if (action instanceof StaticAction(ClickEvent.RunCommand(String command))) {
//$$                 CompoundTag tag = new CompoundTag();
//$$                 tag.putString(DialogWarning.COMMAND_KEY, command);
//$$                 return DialogWarning.customDialogAction(tag);
//$$             }
//$$             return action;
//$$         });
//$$     }
//$$ }
//#endif
