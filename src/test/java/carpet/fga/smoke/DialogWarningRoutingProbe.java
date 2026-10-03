//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.DialogWarning;
//$$ import carpet.fga.FGASettings;
//$$ import com.google.gson.JsonPrimitive;
//$$ import com.mojang.serialization.Codec;
//$$ import com.mojang.serialization.JsonOps;
//$$ import net.fabricmc.api.DedicatedServerModInitializer;
//$$ import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
//$$ import net.minecraft.network.chat.ClickEvent;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.server.dialog.*;
//$$ import net.minecraft.server.dialog.action.*;
//$$ import net.minecraft.server.dialog.input.BooleanInput;
//$$ import net.minecraft.server.dialog.input.TextInput;
//$$ import java.util.List;
//$$ import java.util.Map;
//$$ import java.util.Optional;
//$$
//$$ /** Test mod only: checks the published JAR's actual required codec Mixins in a disposable world. */
//$$ public final class DialogWarningRoutingProbe implements DedicatedServerModInitializer {
//$$     private int checks;
//$$
//$$     @Override public void onInitializeServer() {
//$$         ServerLifecycleEvents.SERVER_STARTED.register(server -> {
//$$             boolean previous = FGASettings.removeDialogWarning;
//$$             try {
//$$                 FGASettings.removeDialogWarning = true;
//$$                 for (String command : new String[]{"!!spbridge config backup on", "!!help", "hello", "seed",
//$$                         "/highlight 1 64 2", "/highlight clear", "/renamedHighlight 1 64 2", "/unknown"}) {
//$$                     var event = new ClickEvent.RunCommand(command);
//$$                     require(event.equals(roundTrip(ClickEvent.CODEC, event)), "Native chat/client click: " + command);
//$$                 }
//$$                 for (String command : new String[]{"/carpet removeDialogWarning true", "/mapLoad pause", "/say hello"}) {
//$$                     ClickEvent event = roundTrip(ClickEvent.CODEC, new ClickEvent.RunCommand(command));
//$$                     require(event instanceof ClickEvent.Custom, "Server command must bypass confirmation: " + command);
//$$                     var custom = (ClickEvent.Custom) event;
//$$                     require(custom.id().equals(DialogWarning.ACTION_ID), "Original custom payload ID");
//$$                     require(command.equals(custom.payload().orElseThrow().asCompound().orElseThrow()
//$$                             .getString(DialogWarning.COMMAND_KEY).orElseThrow()), "Original command preserved");
//$$                 }
//$$                 for (ClickEvent event : new ClickEvent[]{new ClickEvent.SuggestCommand("!!help"),
//$$                         new ClickEvent.SuggestCommand("/carpet"), new ClickEvent.CopyToClipboard("!!help")}) {
//$$                     require(event.equals(roundTrip(ClickEvent.CODEC, event)), "Other click types unchanged");
//$$                 }
//$$                 for (String command : new String[]{"!!spbridge config backup on", "/highlight 1 64 2"}) {
//$$                     var event = new ClickEvent.RunCommand(command);
//$$                     Action action = roundTrip(Action.CODEC, new StaticAction(event));
//$$                     require(event.equals(action.createAction(Map.of()).orElseThrow()), "Native static dialog: " + command);
//$$                 }
//$$                 for (String command : new String[]{"/say hello", "say hello"}) {
//$$                     Action action = roundTrip(Action.CODEC, new StaticAction(new ClickEvent.RunCommand(command)));
//$$                     require(action.createAction(Map.of()).orElseThrow() instanceof ClickEvent.Custom,
//$$                             "Server static dialog supports optional slash");
//$$                 }
//$$                 for (String command : new String[]{"!!spbridge config backup $(text)", "/highlight $(text)",
//$$                         "say $(text)", "/say $(text) $(flag)"}) {
//$$                     var template = ParsedTemplate.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(command)).getOrThrow();
//$$                     var inputData = new CommonDialogData(Component.literal("Routing probe"), Optional.empty(),
//$$                             true, false, DialogAction.CLOSE, List.of(), List.of(
//$$                             new Input("text", new TextInput(200, Component.literal("Text"), true, "", 32, Optional.empty())),
//$$                             new Input("flag", new BooleanInput(Component.literal("Flag"), false, "on", "off"))));
//$$                     var dialog = new NoticeDialog(inputData, new ActionButton(
//$$                             new CommonButtonData(Component.literal("Run"), 150), Optional.of(new CommandTemplate(template))));
//$$                     var decoded = (NoticeDialog) roundTrip(Dialog.DIRECT_CODEC, dialog);
//$$                     Action action = decoded.action().action().orElseThrow();
//$$                     if (command.startsWith("say ") || command.startsWith("/say ")) {
//$$                         var event = (ClickEvent.Custom) action.createAction(Map.of()).orElseThrow();
//$$                         var tag = event.payload().orElseThrow().asCompound().orElseThrow();
//$$                         require(command.equals(tag.getString(DialogWarning.COMMAND_KEY).orElseThrow()), "Dynamic command preserved");
//$$                         require(tag.getBoolean(DialogWarning.DYNAMIC_KEY).orElse(false), "Dynamic marker retained");
//$$                         require(tag.getList(DialogWarning.STRING_INPUT_KEY).orElseThrow().size() == 1, "Text input metadata retained");
//$$                         require(tag.getCompound(DialogWarning.BOOLEAN_TAGS_KEY).orElseThrow()
//$$                                 .getCompound("flag").orElseThrow().getString("true").orElseThrow().equals("on"),
//$$                                 "Boolean input metadata retained");
//$$                     } else {
//$$                         require(action instanceof CommandTemplate, "Native client/chat template preserved");
//$$                         require(((CommandTemplate) action).template().instantiate(Map.of("text", "on"))
//$$                                 .equals(command.replace("$(text)", "on")), "Native template substitution retained");
//$$                     }
//$$                     require(DialogWarning.DIALOG_SCOPE.get() == null, "Dialog scope cleared after serialization");
//$$                 }
//$$                 FGASettings.removeDialogWarning = false;
//$$                 var event = new ClickEvent.RunCommand("/say hello");
//$$                 require(event.equals(roundTrip(ClickEvent.CODEC, event)), "Disabled rule keeps native command");
//$$                 var action = roundTrip(Action.CODEC, new StaticAction(event));
//$$                 require(event.equals(action.createAction(Map.of()).orElseThrow()), "Disabled rule keeps native dialog");
//$$                 System.out.println("FGA_DIALOG_ROUTING_PASS: " + checks + " checks");
//$$             } catch (Throwable failure) {
//$$                 System.err.println("FGA_DIALOG_ROUTING_FAIL: " + failure);
//$$                 failure.printStackTrace();
//$$             } finally {
//$$                 FGASettings.removeDialogWarning = previous;
//$$                 DialogWarning.DIALOG_SCOPE.remove();
//$$                 server.halt(false);
//$$             }
//$$         });
//$$     }
//$$
//$$     private static <T> T roundTrip(Codec<T> codec, T value) {
//$$         return codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow()).getOrThrow();
//$$     }
//$$
//$$     private void require(boolean condition, String message) {
//$$         if (!condition) throw new AssertionError(message);
//$$         checks++;
//$$     }
//$$ }
//#endif
