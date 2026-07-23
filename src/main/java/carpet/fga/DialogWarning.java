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
