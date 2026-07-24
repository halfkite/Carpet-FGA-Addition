//#if MC >= 1.21.8
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.DialogWarning;
//$$ import carpet.fga.FGASettings;
//$$ import net.minecraft.commands.Commands;
//$$ import net.minecraft.commands.functions.StringTemplate;
//$$ import net.minecraft.nbt.ByteTag;
//$$ import net.minecraft.nbt.FloatTag;
//$$ import net.minecraft.nbt.StringTag;
//$$ import net.minecraft.nbt.Tag;
//$$ import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.network.ServerCommonPacketListenerImpl;
//$$ import net.minecraft.server.network.ServerGamePacketListenerImpl;
//$$ import org.spongepowered.asm.mixin.Final;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ import java.util.HashMap;
//$$ import java.util.List;
//$$ import java.util.Map;
//$$
//$$ @Mixin(ServerCommonPacketListenerImpl.class)
//$$ public abstract class DialogWarningServerCommonPacketListenerMixin {
//$$     @Shadow
//$$     @Final
//$$     protected MinecraftServer server;
//$$
//$$     @Inject(
//$$         method = "handleCustomClickAction",
//$$         at = @At("HEAD"),
//$$         cancellable = true
//$$     )
//$$     private void carpetFga$handleCustomClickAction(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
//$$         if (!FGASettings.removeDialogWarning || !packet.id().equals(DialogWarning.ACTION_ID)
//$$                 || !((Object) this instanceof ServerGamePacketListenerImpl gameListener)) {
//$$             return;
//$$         }
//$$
//$$         ServerPlayer player = gameListener.player;
//$$         packet.payload().flatMap(Tag::asCompound).ifPresent(root ->
//$$                 root.getString(DialogWarning.COMMAND_KEY).ifPresent(command -> {
//$$                     if (root.getBooleanOr(DialogWarning.DYNAMIC_KEY, false)) {
//$$                         command = carpetFga$substituteTemplate(root, command);
//$$                     }
//$$                     Commands commands = server.getCommands();
//$$                     commands.performPrefixedCommand(player.createCommandSourceStack(), command);
//$$                 }));
//$$         ci.cancel();
//$$     }
//$$
//$$     private static String carpetFga$substituteTemplate(net.minecraft.nbt.CompoundTag root, String command) {
//$$         Map<String, String> variables = new HashMap<>();
//$$         for (Map.Entry<String, Tag> entry : root.entrySet()) {
//$$             String key = entry.getKey();
//$$             if (key.startsWith(DialogWarning.ACTION_NAMESPACE + ":")) {
//$$                 continue;
//$$             }
//$$             Tag value = entry.getValue();
//$$             String replacement = switch (value) {
//$$                 case StringTag(String stringValue) -> root.getListOrEmpty(DialogWarning.STRING_INPUT_KEY)
//$$                         .contains(StringTag.valueOf(key)) ? StringTag.escapeWithoutQuotes(stringValue) : stringValue;
//$$                 case ByteTag(byte booleanValue) -> {
//$$                     String booleanKey = booleanValue == 0 ? "false" : "true";
//$$                     yield root.getCompoundOrEmpty(DialogWarning.BOOLEAN_TAGS_KEY)
//$$                             .getCompoundOrEmpty(key).getStringOr(booleanKey, booleanKey);
//$$                 }
//$$                 case FloatTag(float floatValue) -> (float) (int) floatValue == floatValue
//$$                         ? Integer.toString((int) floatValue) : Float.toString(floatValue);
//$$                 default -> throw new IllegalStateException("Unexpected dialog input: " + value);
//$$             };
//$$             variables.put(key, replacement);
//$$         }
//$$         StringTemplate template = StringTemplate.fromString(command);
//$$         List<String> values = template.variables().stream().map(key -> variables.getOrDefault(key, "")).toList();
//$$         return template.substitute(values);
//$$     }
//$$ }
//#endif
