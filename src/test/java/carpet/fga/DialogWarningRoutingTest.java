//#if MC == 26.3
//$$ package carpet.fga;
//$$
//$$ import com.mojang.brigadier.CommandDispatcher;
//$$ import com.mojang.brigadier.builder.LiteralArgumentBuilder;
//$$ import org.junit.jupiter.api.Test;
//$$ import static org.junit.jupiter.api.Assertions.*;
//$$
//$$ final class DialogWarningRoutingTest {
//$$     private final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
//$$
//$$     DialogWarningRoutingTest() {
//$$         for (String root : new String[]{"carpet", "say", "seed", "mapLoad", "example:action"}) {
//$$             dispatcher.register(LiteralArgumentBuilder.literal(root));
//$$         }
//$$     }
//$$
//$$     @Test void chatAndClientOnlyCommandsDoNotBecomeServerActions() {
//$$         for (String command : new String[]{"!!spbridge config backup on", "!!help", "hello",
//$$                 "/highlight 1 64 2", "/highlight clear", "/renamedHighlight 1 64 2", "/unknown"}) {
//$$             assertFalse(isServerCommand(command), command);
//$$         }
//$$     }
//$$
//$$     @Test void registeredCommandsRemainEligibleIncludingBareDialogCommands() {
//$$         for (String command : new String[]{"/carpet removeDialogWarning true", "/mapLoad pause",
//$$                 "/say hello", "say $(text)", "/seed", "/example:action argument"}) {
//$$             assertTrue(isServerCommand(command), command);
//$$         }
//$$     }
//$$
//$$     @Test void onlyTheExactRegisteredRootMatches() {
//$$         for (String command : new String[]{"", "/", " /say hello", "/ say hello", "/sayElse hello",
//$$                 "/SAY hello", "/say\thello", "//say hello", "/$(command) hello"}) {
//$$             assertFalse(isServerCommand(command), command);
//$$         }
//$$         assertTrue(isServerCommand("/say $(text)"));
//$$     }
//$$
//$$     @Test void commandRegistrationChangesAreObserved() {
//$$         assertFalse(isServerCommand("/lateCommand argument"));
//$$         dispatcher.register(LiteralArgumentBuilder.literal("lateCommand"));
//$$         assertTrue(isServerCommand("/lateCommand argument"));
//$$     }
//$$
//$$     private boolean isServerCommand(String command) {
//$$         return DialogWarning.hasServerCommandRoot(command, root -> dispatcher.getRoot().getChild(root) != null);
//$$     }
//$$ }
//#endif
