//#if MC >= 26.1.2
//$$ package carpet.fga;
//$$
//$$ import net.minecraft.nbt.CompoundTag;
//$$ import net.minecraft.network.chat.ClickEvent;
//$$ import net.minecraft.resources.Identifier;
//$$ import net.minecraft.server.dialog.Dialog;
//$$ import net.minecraft.server.dialog.action.Action;
//$$ import net.minecraft.server.dialog.action.CustomAll;
//$$
//$$ import java.util.Optional;
//$$
//$$ /** Adapted from remove-dialog-warning by Drex under the MIT License. */
//$$ public final class DialogWarning {
//$$     public static final String ACTION_NAMESPACE = "carpet-fga-addition";
//$$     public static final String COMMAND_KEY = ACTION_NAMESPACE + ":command";
//$$     public static final String DYNAMIC_KEY = ACTION_NAMESPACE + ":dynamic";
//$$     public static final String BOOLEAN_TAGS_KEY = ACTION_NAMESPACE + ":boolean_input";
//$$     public static final String STRING_INPUT_KEY = ACTION_NAMESPACE + ":string_input";
//$$     public static final Identifier ACTION_ID = Identifier.fromNamespaceAndPath(ACTION_NAMESPACE, "run_command");
//$$     public static final ThreadLocal<Dialog> DIALOG_SCOPE = new ThreadLocal<>();
//$$
//$$     private DialogWarning() {
//$$     }
//$$
//$$     public static Action customDialogAction(CompoundTag tag) {
//$$         return new CustomAll(ACTION_ID, Optional.of(tag));
//$$     }
//$$
//$$     public static ClickEvent customClickAction(CompoundTag tag) {
//$$         return new ClickEvent.Custom(ACTION_ID, Optional.of(tag));
//$$     }
//#if MC == 26.3
//$$
//$$     /** Keep client-only commands on the native click path instead of executing them on the server. */
//$$     public static boolean isServerCommand(String command) {
//$$         var server = carpet.CarpetServer.minecraft_server;
//$$         return server != null && hasServerCommandRoot(command,
//$$                 root -> server.getCommands().getDispatcher().getRoot().getChild(root) != null);
//$$     }
//$$
//$$     static boolean hasServerCommandRoot(String command, java.util.function.Predicate<String> registeredRoot) {
//$$         int start = command.startsWith("/") ? 1 : 0;
//$$         int end = command.indexOf(' ', start);
//$$         String root = command.substring(start, end < 0 ? command.length() : end);
//$$         return !root.isEmpty() && registeredRoot.test(root);
//$$     }
//#endif
//$$ }
//#else
//#if MC >= 1.21.8
//$$ package carpet.fga;
//$$
//$$ import net.minecraft.nbt.CompoundTag;
//$$ import net.minecraft.network.chat.ClickEvent;
//$$ import net.minecraft.resources.ResourceLocation;
//$$ import net.minecraft.server.dialog.Dialog;
//$$ import net.minecraft.server.dialog.action.Action;
//$$ import net.minecraft.server.dialog.action.CustomAll;
//$$
//$$ import java.util.Optional;
//$$
//$$ /** Adapted from remove-dialog-warning by Drex under the MIT License. */
//$$ public final class DialogWarning {
//$$     public static final String ACTION_NAMESPACE = "carpet-fga-addition";
//$$     public static final String COMMAND_KEY = ACTION_NAMESPACE + ":command";
//$$     public static final String DYNAMIC_KEY = ACTION_NAMESPACE + ":dynamic";
//$$     public static final String BOOLEAN_TAGS_KEY = ACTION_NAMESPACE + ":boolean_input";
//$$     public static final String STRING_INPUT_KEY = ACTION_NAMESPACE + ":string_input";
//$$     public static final ResourceLocation ACTION_ID = ResourceLocation.fromNamespaceAndPath(ACTION_NAMESPACE, "run_command");
//$$     public static final ThreadLocal<Dialog> DIALOG_SCOPE = new ThreadLocal<>();
//$$
//$$     private DialogWarning() {
//$$     }
//$$
//$$     public static Action customDialogAction(CompoundTag tag) {
//$$         return new CustomAll(ACTION_ID, Optional.of(tag));
//$$     }
//$$
//$$     public static ClickEvent customClickAction(CompoundTag tag) {
//$$         return new ClickEvent.Custom(ACTION_ID, Optional.of(tag));
//$$     }
//$$ }
//#endif
//#endif
